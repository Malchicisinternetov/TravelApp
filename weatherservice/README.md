# Weather service

Spring Boot, порт **8085**, без базы данных. Все запросы требуют `Authorization: Bearer <JWT>` от userservice.
Текущая погода и прогноз на 7 дней — Open-Meteo; поиск населённых пунктов — его Geocoding API (GeoNames).
Интеграция и поиск координат находятся в этом сервисе, отдельный сервис карт не нужен.

## Запуск

Укажите общий с остальными сервисами `JWT_SECRET` (не менее 32 байт), при необходимости `JWT_ISSUER` (по умолчанию `travelapp`).
Из корня проекта:

```sh
./mvnw -pl weatherservice -am package -DskipTests
java -jar weatherservice/target/weatherservice-0.0.1-SNAPSHOT.jar
```

В Windows используйте `mvnw.cmd`. Дополнительные переменные окружения:

| Переменная | По умолчанию |
| --- | --- |
| WEATHER_SERVICE_PORT | 8085 |
| WEATHER_FORECAST_URL | https://api.open-meteo.com |
| WEATHER_GEOCODING_URL | https://geocoding-api.open-meteo.com |
| WEATHER_API_KEY | пусто |

Бесплатные endpoints работают без ключа и предназначены для **некоммерческого использования**.
Для коммерческого продукта настройте customer-адреса и ключ по [условиям Open-Meteo](https://open-meteo.com/en/terms).
При отображении данных сохраняйте атрибуцию Open-Meteo / CC BY 4.0 и GeoNames, как во frontend.

## REST API

- `GET /weather/locations?city=Москва` — до 8 вариантов с названием, регионом, страной и координатами. Пустой результат — `[]`.
- `GET /weather?city=Москва` — погода для первого результата поиска. Для неоднозначных названий сначала используйте поиск; можно уточнить `city=Paris, France`.
- `GET /weather?latitude=55.7558&longitude=37.6173` — погода по координатам WGS84.

Город и координаты нельзя передавать одновременно; широта −90…90, долгота −180…180.
Ответ: `location`, `timezone`, `current`, `forecast[]`, `provider`.
`current`: местное `time`, `temperature`, `feelsLike` (°C), `humidity` (%), `windSpeed` (м/с), WMO `weatherCode`, `isDay`.
Дневной прогноз: `date`, `weatherCode`, `min`, `max`, `precipitationProbability` (%), местные `sunrise` и `sunset`.
Некоторые показатели могут быть `null` (например, восход в полярную ночь); frontend показывает прочерк.

Коды ошибок: 400 — параметры, 401 — JWT, 404 — город не найден, 503 — внешний API недоступен или вернул некорректный ответ.
Таймаут подключения — 3 с, чтения — 8 с. JWT пользователя внешнему API не передаётся.

Документация: [прогноз](https://open-meteo.com/en/docs), [поиск](https://open-meteo.com/en/docs/geocoding-api).
Тесты без внешней сети: `./mvnw -pl weatherservice -am test`.

Общие Kafka и Redis: [запуск и настройки](../INFRASTRUCTURE.md).
