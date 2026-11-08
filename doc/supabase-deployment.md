# Supabase: настройка и повторное развертывание

## Текущая конфигурация

- Hosted-проект: `couple-messenger`.
- Project ref: `nmmwosaohnpckbrwyyna`.
- Supabase CLI: `3.0.0`.
- Локальная конфигурация: `supabase/config.toml`.
- Базовая миграция: `supabase/migrations/20261108163512_initial_schema.sql`.

Базовая миграция восстанавливает таблицу `public.messages`, её identity-ключ, RLS и две политики для роли `anon`, а также добавление таблицы в Realtime publication. Она уже отмечена как применённая в истории hosted-проекта.

## Локальные секреты

Пароль базы данных хранится только в `supabase/.env.local`:

```properties
SUPABASE_DB_PASSWORD=...
```

Файл исключён из Git через `supabase/.gitignore`. Не коммитьте access token, пароль PostgreSQL, service-role key, SMTP/OAuth-секреты и реальные пользовательские данные.

## Первичное подключение на другом компьютере

```bash
supabase login
supabase link --project-ref nmmwosaohnpckbrwyyna
```

## Проверка состояния

```bash
# Сравнить локальные и удалённые миграции
supabase migration list --remote

# Проверить, какие миграции были бы применены
supabase db push --dry-run --remote

# Сравнить поддерживаемые настройки config.toml с hosted-проектом
supabase config diff
```

## Развертывание изменений

```bash
# 1. Предварительная проверка без изменений на сервере
supabase db push --dry-run --remote

# 2. Применение только ещё не выполненных миграций
supabase db push --remote

# 3. Применение настроек только после проверки config diff
supabase config push
```

`db push` не пересоздаёт существующую базу и применяет только новые миграции. Не используйте `supabase db reset --remote` для обычного развертывания: эта команда удаляет удалённую структуру и создаёт её заново.

## Создание следующей миграции

```bash
supabase migration new <change_name>
```

После этого отредактируйте созданный SQL-файл в `supabase/migrations/`, проверьте его локально, закоммитьте и только затем выполните `db push`.

## Локальная проверка

Для локального стека требуется запущенный Docker Desktop:

```bash
supabase start

# Пересоздаёт только локальную базу, применяет все миграции и seed.sql
supabase db reset

supabase stop
```

## Получение изменений, сделанных через Dashboard

```bash
# Настройки Auth, API, Realtime, Storage и других поддерживаемых секций
supabase config pull

# Изменения схемы в виде новой миграции
supabase db pull --remote
```

После выполнения команд проверьте созданные файлы и закоммитьте их. Не принимайте автоматически сгенерированные изменения без просмотра SQL и конфигурации.

## Особенность первичной выгрузки

При первичной настройке `db pull` и `pg_dump` обрывались pooler-соединением с `ECONNRESET`. Текущая базовая миграция восстановлена через read-only SQL-запросы к системному каталогу PostgreSQL, а затем отмечена как применённая:

```bash
supabase migration repair --status applied 20261108163512 --remote
```

Повторно отмечать эту миграцию не нужно. Команда `migration repair` предназначена только для исправления истории и не должна использоваться вместо обычных миграций.
