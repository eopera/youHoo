create table public.messages (
    id bigint generated always as identity primary key,
    text text not null,
    sender text not null,
    created_at timestamp with time zone not null default now()
);

alter table public.messages enable row level security;

create policy "Allow anonymous reads"
    on public.messages
    for select
    to anon
    using (true);

create policy "Allow anonymous inserts"
    on public.messages
    for insert
    to anon
    with check (true);

alter publication supabase_realtime add table public.messages;
