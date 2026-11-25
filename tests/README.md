# Интеграционные тесты

## Внутри сервисов — Java

Из корня проекта:

```sh
sh ./mvnw -Pservice-integration test
```

Профиль выбирает JUnit-тег `integration`. Проверяются реальные Spring-контексты,
фильтры безопасности, контроллеры, сервисы, JPA и транзакции. Хранилище — H2;
границы соседних сервисов заменены Mockito, погодный и валютный провайдеры —
локальными HTTP-серверами. Kafka и Redis для этого уровня выключены.
Проверки PostgreSQL и реального обмена Kafka выполняются следующим уровнем.

Нужны Java 21 и значения-пустышки для переменных приложения. Список есть в
[CI action](../.github/actions/test-environment/action.yml); настоящий `../.env` не нужен.
Обычный `sh ./mvnw test` выполняет unit- и внутрисервисные тесты,
`sh ./mvnw -Pci test` — только unit-тесты.

## Между сервисами — Python

Нужен Docker с Compose v2; Python устанавливается внутри тестового образа.
Из корня проекта в WSL/Linux:

```sh
sh tests/run.sh
```

Скрипт собирает образы, запускает отдельный [compose.test.yaml](../compose.test.yaml),
возвращает код завершения pytest и всегда удаляет тестовые контейнеры и volumes.
Логи и JUnit XML остаются в `tests/reports/`.

Стек содержит семь настоящих Java-сервисов, PostgreSQL с четырьмя отдельными БД,
Kafka, Redis, Python pytest runner и локальный сервер внешних API.
Production Compose, `../.env`, настоящие Telegram-аккаунты и внешние API не используются.
Тестовые пароли и подписи JWT публичны и предназначены только для этого стека.
Порты на хост не публикуются, production volumes не подключаются.
Для параллельных запусков задайте разные `TRAVEL_TEST_PROJECT`.
Тесты внутри одного стека запускаются последовательно: сценарии ошибок временно меняют
ответы тестового провайдера и настройки тестового Redis.

Для диагностики без автоматической остановки:

```sh
docker compose -p travelapp-tests --env-file tests/.env.example -f compose.test.yaml build
docker compose -p travelapp-tests --env-file tests/.env.example -f compose.test.yaml up -d --wait userservice tripservice expensesservice weatherservice currencyservice botservice notificationservice
docker compose -p travelapp-tests --env-file tests/.env.example -f compose.test.yaml run --rm tests pytest -q --junitxml=/reports/multiservice.xml
docker compose -p travelapp-tests --env-file tests/.env.example -f compose.test.yaml down --volumes --remove-orphans
```

Можно выбрать файл или сценарий, например `pytest -q multiservice/test_messaging.py`.
HTTP-запросы и ожидание Kafka ограничены таймаутами; изменения с неопределённым результатом
не повторяются автоматически. Имена аккаунтов, поездок, chat ID и request ID уникальны.
Проверки состояния выполняются через REST, Kafka replies и публичные ответы Telegram-заглушки.
Подготовка не записывает бизнес-данные напрямую в базы.

## Сценарии

| Область | Что проверяем |
| --- | --- |
| Аккаунты | Регистрация, слабые/повторные пароли, вход, приватность профиля, настройки, справочник имён |
| Сессии и JWT | Refresh cookie, logout, срок, issuer, audience, subject, not-before во всех пяти HTTP API |
| Telegram | Привязка веб-аккаунта одноразовым кодом, конфликты, запрет повторного использования, отвязка, групповой чат |
| Поездки | Маршрут с пересадками и местными временами, сохранение изменений, rollback, список доступных поездок |
| Права | OWNER/MEMBER/READ_ONLY, последний владелец, передача владения, удалённый участник, несуществующий пользователь |
| Расходы | Оплата за других, createdBy/paidBy, равные/произвольные доли, личная история и сводка, взаимозачёт долгов |
| Валюты расходов | Раздельный учёт, точное округление USD/JPY/KWD, недопустимые суммы/доли, удаление и изоляция поездок |
| Kafka | Полная цепочка bot/users/trips/expenses, корреляция, повторная доставка, конфликт UUID, просроченные и сломанные сообщения |
| Redis | Настоящий кеш погоды/курсов с TTL, повторное чтение, обновление после удаления ключа, запрет команд при отказе записи |
| Внешние API | Поиск города/координаты, прогноз, курсы/конвертация, округление, 429/503, восстановление, ошибки не кешируются |
| Уведомления | Реальный планировщик → Kafka → поездки/получатели → Telegram, окно 3 часа, согласие, проверенная привязка, отсутствие дублей |
| Конкурентность внутри сервисов | Параллельное изменение владельцев и отправка напоминания, целостность транзакций |

Telegram, Open-Meteo и Frankfurter заменены только на внешней HTTP-границе.
Адрес Telegram API настраивается `TELEGRAM_API_URL`; по умолчанию остаётся реальный Telegram.
Сборка образов требует доступа к Maven Central, Docker Hub и PyPI.
Frontend в этот стек не включён; его unit-тесты запускаются отдельно.

## CI

Workflow выполняет три независимых задания: unit (Java + frontend), внутрисервисные
Java integration и Python multiservice. Каждый job создаёт временный `.env`
с пустышками и удаляет его в конце. GitHub Secrets не нужны.
Отчёты сохраняются как artifacts на 7 дней; для мультисервисных тестов также сохраняются логи Compose.
Сбой любого уровня завершает workflow с ошибкой.
