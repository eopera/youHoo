alter table public.messages
    add column delivered_at timestamp with time zone,
    add column read_at timestamp with time zone;

create or replace function public.mark_messages_delivered(
    p_message_ids bigint[],
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
    p_message_ids bigint[],
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

revoke all on function public.mark_messages_delivered(bigint[], uuid) from public;
revoke all on function public.mark_messages_read(bigint[], uuid) from public;
grant execute on function public.mark_messages_delivered(bigint[], uuid) to anon;
grant execute on function public.mark_messages_read(bigint[], uuid) to anon;
