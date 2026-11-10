create table public.users (
    id uuid primary key default gen_random_uuid(),
    device_id uuid not null unique,
    display_name text not null check (char_length(trim(display_name)) between 1 and 50),
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

insert into public.users (device_id, display_name)
select device_id, sender_name
from public.device_tokens;

create table public.conversations (
    id uuid primary key default gen_random_uuid(),
    is_group boolean not null default false,
    title text,
    direct_key text unique,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint conversations_title_length check (
        title is null or char_length(title) between 1 and 150
    ),
    constraint conversations_direct_key check (is_group = (direct_key is null))
);

insert into public.conversations (id, is_group, title)
values (
    '00000000-0000-0000-0000-000000000001'::uuid,
    true,
    'Family'
);

alter table public.messages rename to legacy_messages;

create table public.messages (
    id uuid primary key,
    conversation_id uuid not null references public.conversations(id) on delete restrict,
    sender_id uuid references public.users(id) on delete set null,
    sender text not null,
    sender_device_id uuid,
    text text not null,
    metadata jsonb not null default '{}'::jsonb,
    parent_id uuid references public.messages(id) on delete set null,
    created_at timestamp with time zone not null default now(),
    delivered_at timestamp with time zone,
    read_at timestamp with time zone,
    deleted_at timestamp with time zone
);

insert into public.messages (
    id,
    conversation_id,
    sender,
    sender_device_id,
    text,
    created_at,
    delivered_at,
    read_at
)
select
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000001'::uuid,
    sender,
    sender_device_id,
    text,
    created_at,
    delivered_at,
    read_at
from public.legacy_messages;

alter table public.legacy_messages enable row level security;
revoke all on public.legacy_messages from anon, authenticated;
alter publication supabase_realtime drop table public.legacy_messages;
alter publication supabase_realtime add table public.messages;

create index messages_conversation_chronological_idx
    on public.messages (conversation_id, created_at desc, id desc);

create table public.conversation_participants (
    conversation_id uuid not null references public.conversations(id) on delete cascade,
    user_id uuid not null references public.users(id) on delete cascade,
    role text not null default 'member',
    joined_at timestamp with time zone not null default now(),
    last_read_message_id uuid references public.messages(id) on delete set null,
    primary key (conversation_id, user_id),
    constraint conversation_participants_role check (role in ('admin', 'member'))
);

create index conversation_participants_user_idx
    on public.conversation_participants (user_id);

alter table public.users enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.conversation_participants enable row level security;

create policy "Registered users are visible in the contact directory"
    on public.users
    for select
    to anon, authenticated
    using (true);

create policy "Prototype anonymous message reads"
    on public.messages
    for select
    to anon
    using (true);

create policy "Prototype anonymous message inserts"
    on public.messages
    for insert
    to anon
    with check (true);

alter table public.device_tokens
    add column user_id uuid references public.users(id) on delete cascade;

update public.device_tokens tokens
set user_id = users.id
from public.users users
where users.device_id = tokens.device_id;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger users_set_updated_at
    before update on public.users
    for each row
    execute function public.set_updated_at();

create trigger conversations_set_updated_at
    before update on public.conversations
    for each row
    execute function public.set_updated_at();

create trigger on_message_created_enqueue_push
    after insert on public.messages
    for each row
    execute function public.enqueue_message_push_notification();

create or replace function public.register_user(
    p_device_id uuid,
    p_display_name text
)
returns public.users
language plpgsql
security definer
set search_path = ''
as $$
declare
    result public.users;
begin
    if p_device_id is null then
        raise exception 'Device ID is required';
    end if;
    if char_length(trim(p_display_name)) not between 1 and 50 then
        raise exception 'Invalid display name length';
    end if;

    insert into public.users (device_id, display_name)
    values (p_device_id, trim(p_display_name))
    on conflict (device_id) do update
    set display_name = excluded.display_name
    returning * into result;

    update public.device_tokens
    set user_id = result.id, sender_name = result.display_name
    where device_id = p_device_id;

    return result;
end;
$$;

create or replace function public.register_device_token(
    p_device_id uuid,
    p_fcm_token text,
    p_sender_name text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if char_length(p_fcm_token) not between 1 and 4096 then
        raise exception 'Invalid FCM token length';
    end if;
    if char_length(trim(p_sender_name)) not between 1 and 50 then
        raise exception 'Invalid sender name length';
    end if;

    insert into public.device_tokens (device_id, fcm_token, sender_name, user_id)
    values (
        p_device_id,
        p_fcm_token,
        trim(p_sender_name),
        (select id from public.users where device_id = p_device_id)
    )
    on conflict (device_id) do update
    set
        fcm_token = excluded.fcm_token,
        sender_name = excluded.sender_name,
        user_id = excluded.user_id,
        updated_at = now();
end;
$$;

create or replace function public.get_or_create_direct_conversation(
    p_user_id uuid,
    p_other_user_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    result uuid;
    pair_key text;
begin
    if p_user_id is null or p_other_user_id is null or p_user_id = p_other_user_id then
        raise exception 'Two different users are required';
    end if;
    if not exists (select 1 from public.users where id = p_user_id)
        or not exists (select 1 from public.users where id = p_other_user_id) then
        raise exception 'Both users must be registered';
    end if;

    pair_key := least(p_user_id::text, p_other_user_id::text)
        || ':' || greatest(p_user_id::text, p_other_user_id::text);

    insert into public.conversations (is_group, direct_key)
    values (false, pair_key)
    on conflict (direct_key) do nothing
    returning id into result;

    if result is null then
        select id into result from public.conversations where direct_key = pair_key;
    end if;

    insert into public.conversation_participants (conversation_id, user_id)
    values (result, p_user_id), (result, p_other_user_id)
    on conflict do nothing;

    return result;
end;
$$;

create or replace function public.mark_messages_delivered(
    p_message_ids uuid[],
    p_device_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_message_ids is null or cardinality(p_message_ids) = 0 then
        return;
    end if;
    if p_device_id is null then
        raise exception 'Device ID is required';
    end if;

    update public.messages
    set delivered_at = coalesce(delivered_at, now())
    where id = any(p_message_ids)
      and sender_device_id is distinct from p_device_id;
end;
$$;

create or replace function public.mark_messages_read(
    p_message_ids uuid[],
    p_device_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_message_ids is null or cardinality(p_message_ids) = 0 then
        return;
    end if;
    if p_device_id is null then
        raise exception 'Device ID is required';
    end if;

    update public.messages
    set
        delivered_at = coalesce(delivered_at, now()),
        read_at = coalesce(read_at, now())
    where id = any(p_message_ids)
      and sender_device_id is distinct from p_device_id;
end;
$$;

create or replace function public.get_conversation_messages(
    p_conversation_id uuid,
    p_page_size integer default 50,
    p_before_created_at timestamp with time zone default null,
    p_before_id uuid default null
)
returns setof public.messages
language sql
stable
security invoker
set search_path = ''
as $$
    select *
    from public.messages
    where conversation_id = p_conversation_id
      and deleted_at is null
      and (
          (p_before_created_at is null and p_before_id is null)
          or (
              p_before_created_at is not null
              and p_before_id is not null
              and (created_at, id) < (p_before_created_at, p_before_id)
          )
      )
    order by created_at desc, id desc
    limit least(greatest(p_page_size, 1), 100);
$$;

drop function public.mark_messages_delivered(bigint[], uuid);
drop function public.mark_messages_read(bigint[], uuid);
revoke all on function public.register_user(uuid, text) from public;
revoke all on function public.get_or_create_direct_conversation(uuid, uuid) from public;
revoke all on function public.mark_messages_delivered(uuid[], uuid) from public;
revoke all on function public.mark_messages_read(uuid[], uuid) from public;
revoke all on function public.get_conversation_messages(uuid, integer, timestamp with time zone, uuid) from public;
grant execute on function public.register_user(uuid, text) to anon;
grant execute on function public.get_or_create_direct_conversation(uuid, uuid) to anon;
grant execute on function public.mark_messages_delivered(uuid[], uuid) to anon;
grant execute on function public.mark_messages_read(uuid[], uuid) to anon;
grant execute on function public.get_conversation_messages(uuid, integer, timestamp with time zone, uuid) to anon, authenticated;
