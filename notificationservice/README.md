# Напоминания о вылетах

Сервис раз в минуту получает из tripservice участки маршрутов с отправлением
в ближайшие 3 часа и присылает участникам сообщение:

> Скоро ваш вылет из Москва в Стамбул, не пропустите!
> Отправление: 01.11.2026 13:00 (Europe/Moscow).
> Поездка: Отпуск

Время сравнивается в UTC с учётом часового пояса отправления. Текст содержит
местное время и часовой пояс. Сейчас каждый leg считается перелётом.
Направление поездки без участков маршрута не создаёт напоминаний.

Первое уведомление приходит при входе в трёхчасовое окно, обычно с задержкой
не больше интервала проверки. Если сервис перезапустили или поездку создали позже,
напоминание отправится при следующей проверке, пока вылет ещё не состоялся.
После времени отправления сообщения не отправляются.
Пересадки учитываются: напоминание создаётся отдельно для каждого участка и участника.

Telegram ID берётся только из подтверждённой привязки в userservice:
участник должен ранее выполнить /start или /link в боте.
Участники всех ролей, включая READ_ONLY, получают сообщения.
notificationMethod=null, пустая строка или TELEGRAM разрешают Telegram-уведомления;
другие значения (например EMAIL или NONE) их отключают.
Удалённые из поездки участники не входят в последующие проверки.

## Запуск

Нужна отдельная PostgreSQL БД; Hibernate создаёт таблицу reminder_deliveries.
Она хранит отправки и время следующей попытки, поэтому её нужно сохранять при
перезапуске приложения.

| Переменная | Где задать | Назначение |
| --- | --- | --- |
| NOTIFICATION_SERVICE_SECRET | notificationservice, tripservice, userservice | Одинаковый случайный секрет, минимум 32 UTF-8 байта |
| TELEGRAM_BOT_TOKEN | notificationservice | Тот же токен, что используется в botservice |
| NOTIFICATIONS_POSTGRES_URL | notificationservice | Например jdbc:postgresql://localhost:5432/notifications |
| NOTIFICATIONS_POSTGRES_USERNAME | notificationservice | Пользователь БД |
| NOTIFICATIONS_POSTGRES_PASSWORD | notificationservice | Пароль БД |
| NOTIFICATIONS_POLL_INTERVAL_MS | notificationservice | По умолчанию 60000 |
| NOTIFICATIONS_ENABLED | notificationservice | По умолчанию true; false выключает фоновую проверку |

NOTIFICATION_SERVICE_SECRET должен отличаться от JWT_SECRET и BOT_SERVICE_SECRET.
Сгенерировать значение в WSL: `openssl rand -base64 32`.
Перезапустите tripservice и userservice с новым секретом, затем запускайте:

```powershell
.\mvnw.cmd -pl notificationservice,tripservice,userservice -am install -DskipTests
.\mvnw.cmd -pl notificationservice spring-boot:run
```

notificationservice больше не запускает Telegram long polling и не обрабатывает
команды. Он только вызывает [Telegram sendMessage](https://core.telegram.org/bots/api#sendmessage).
Поэтому может работать с тем же токеном одновременно с botservice.
TELEGRAM_BOT_NAME и серверный порт больше не нужны. JWT_SECRET самому
notificationservice также не требуется.

## Внутренние API

Запросы защищены заголовком X-Notification-Secret:

- GET tripservice/internal/trips/departures?from=...&to=... — вылеты в интервале UTC,
  максимум 6 часов, с актуальными ID участников.
- POST userservice/internal/notifications/telegram-recipients с
  `{"userIds":[1,2]}` — подтверждённые Telegram ID и учёт предпочтений уведомлений.

Обычный пользовательский JWT не заменяет этот секрет. При пустом секрете
внутренние API отключены (503), при неправильном — 401.

## Повторы и ошибки

Отправка учитывается по поездке, участнику, точкам маршрута и абсолютному времени
отправления. Переименование поездки или перестановка участков не создают повтор.
Изменение вылета создаёт новое напоминание. Блокировка записи и уникальный ключ
защищают от параллельных отправок двумя экземплярами сервиса.

Ошибки Telegram повторяются не раньше чем через минуту; для 429 учитывается
retry_after. Ошибка одного участника не мешает остальным.
Успешная отправка сохраняется в БД и пропускается при следующих проверках.
При аварии между принятием сообщения Telegram и фиксацией транзакции в БД
возможен повтор: Telegram sendMessage не предоставляет ключ идемпотентности.
HTTP-ошибки с токеном бота в URL не выводятся в журнал.

Проверка: `./mvnw.cmd -pl notificationservice,tripservice,userservice -am test`.
Используются H2 и HTTP-заглушки; настоящие Telegram-сообщения тесты не отправляют.

Вылеты и получатели запрашиваются через Kafka. Общие Kafka и Redis: [инструкция](../INFRASTRUCTURE.md).

