alter table public.messages
    add column sender_device_id uuid;

create table public.device_tokens (
    device_id uuid primary key,
    fcm_token text not null unique check (char_length(fcm_token) between 1 and 4096),
    sender_name text not null check (char_length(trim(sender_name)) between 1 and 50),
    updated_at timestamp with time zone not null default now()
);

alter table public.device_tokens enable row level security;

create policy "Anonymous devices can register push tokens"
    on public.device_tokens
    for insert
    to anon
    with check (true);

create policy "Anonymous devices can refresh push tokens"
    on public.device_tokens
    for update
    to anon
    using (true)
    with check (true);

grant insert, update on public.device_tokens to anon;

create or replace function public.set_device_token_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger device_tokens_set_updated_at
    before update on public.device_tokens
    for each row
    execute function public.set_device_token_updated_at();

create extension if not exists pg_net;

create or replace function public.enqueue_message_push_notification()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform net.http_post(
        url := 'https://nmmwosaohnpckbrwyyna.supabase.co/functions/v1/send-message-push',
        body := jsonb_build_object(
            'type', 'INSERT',
            'table', 'messages',
            'record', to_jsonb(new)
        ),
        headers := '{"Content-Type":"application/json"}'::jsonb,
        timeout_milliseconds := 5000
    );
    return new;
end;
$$;

create trigger on_message_created_enqueue_push
    after insert on public.messages
    for each row
    execute function public.enqueue_message_push_notification();
