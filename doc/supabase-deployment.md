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


## Push-уведомления через FCM

Миграция `20261108210343_push_notifications.sql` добавляет `messages.sender_device_id`, таблицу `public.device_tokens` и PostgreSQL-триггер на новые сообщения. Миграция `20261108213746_register_device_token_rpc.sql` добавляет проверяемую security-definer RPC `register_device_token`; прямые anonymous insert/update в таблицу токенов после неё запрещены. Миграция `20261109213023_message_delivery_read_state.sql` добавляет shared `delivered_at/read_at` и монотонные RPC для receipt-переходов. Edge Function `send-message-push` проверяет сообщение по его ID, отправляет FCM v1 уведомления другим устройствам с unread count и первым непрочитанным ID, и удаляет недействительные токены. Токены недоступны анонимному чтению через PostgREST.

### Настройка Android

В Firebase Console создайте Android-приложение с package name `com.eopera.youhoo`. Добавьте его публичные клиентские параметры в gitignored `local.properties`:

```properties
FIREBASE_API_KEY=your-web-api-key
FIREBASE_APPLICATION_ID=your-mobilesdk-app-id
FIREBASE_PROJECT_ID=your-firebase-project-id
FIREBASE_SENDER_ID=your-firebase-project-number
```

Эти значения соответствуют `google-services.json`: `api_key.current_key`, `client_info.mobilesdk_app_id`, `project_info.project_id` и `project_info.project_number`. Приложение инициализирует Firebase программно, поэтому plugin `google-services` и файл `google-services.json` не требуются. Пустые значения отключают только push-регистрацию, чат продолжает работать.

### Настройка серверного секрета

В Firebase Console создайте service account с правом отправки Firebase Cloud Messaging. Сохраните весь JSON service account в переменную окружения `FIREBASE_SERVICE_ACCOUNT`, затем передайте её в секреты Supabase:

```bash
supabase secrets set FIREBASE_SERVICE_ACCOUNT="$FIREBASE_SERVICE_ACCOUNT"
```

JSON service account является привилегированным секретом. Не помещайте его в Android `local.properties`, `BuildConfig`, исходный код, APK, Git или логи. Файлы `service-account*.json` и `firebase-service-account*.json` исключены через корневой `.gitignore`.

### Развертывание

```bash
supabase db push --dry-run --remote
supabase db push --remote
supabase functions deploy send-message-push --no-verify-jwt --use-api
```

Перед повторным развертыванием просмотрите SQL и TypeScript. Не используйте `db reset --remote`. Endpoint функции доступен для database webhook без JWT, но принимает только ID реально существующих сообщений. До появления Supabase Auth регистрация и обновление токенов не связаны с authenticated user ID; это ограничение прототипа, а не production authorization.

### Проверка

1. Установите APK с Firebase-параметрами минимум на два устройства и зарегистрируйте разные имена.
2. Разрешите уведомления на Android 13+.
3. Сведите приложение на втором устройстве.
4. Отправьте сообщение с первого устройства.
5. На втором должно появиться уведомление в канале `family_messages`; нажатие открывает чат.
6. Проверьте, что устройство отправителя не получило собственное уведомление.

Без `FIREBASE_SERVICE_ACCOUNT` функция вернёт `503`, если зарегистрированные токены есть. Такая конфигурация не подтверждает доставку FCM.
