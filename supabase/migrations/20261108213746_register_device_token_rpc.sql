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

    insert into public.device_tokens (device_id, fcm_token, sender_name)
    values (p_device_id, p_fcm_token, trim(p_sender_name))
    on conflict (device_id) do update
    set
        fcm_token = excluded.fcm_token,
        sender_name = excluded.sender_name,
        updated_at = now();
end;
$$;

revoke all on function public.register_device_token(uuid, text, text) from public;
grant execute on function public.register_device_token(uuid, text, text) to anon;

drop policy "Anonymous devices can register push tokens" on public.device_tokens;
drop policy "Anonymous devices can refresh push tokens" on public.device_tokens;
revoke insert, update on public.device_tokens from anon;
