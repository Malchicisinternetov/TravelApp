# Currency service

Spring Boot, порт **8086**, без базы данных. Требует общий `JWT_SECRET` и JWT userservice в заголовке `Authorization: Bearer <JWT>`.
Источник — [Frankfurter v2](https://frankfurter.dev/), ключ API не нужен. Поддерживаются обычные валюты ISO 4217, в том числе RUB; криптовалюты и металлы исключены.

## Запуск

Из корня проекта после настройки переменных окружения:

```sh
./mvnw -pl currencyservice -am package -DskipTests
java -jar currencyservice/target/currencyservice-0.0.1-SNAPSHOT.jar
```

Windows: `mvnw.cmd`. Настройки:

- `JWT_SECRET` — общий секрет не менее 32 байт.
- `JWT_ISSUER` — `travelapp` по умолчанию.
- `CURRENCY_SERVICE_PORT` — `8086`.
- `CURRENCY_API_URL` — `https://api.frankfurter.dev`.

## REST API

- `GET /currencies` — список `{code,name}` доступных валют.
- `GET /currencies/rates?base=RUB` — `{base,fetchedAt,provider,rates:[{date,base,quote,rate}]}`.
  `rate` — сколько единиц `quote` приходится на 1 единицу `base`. У каждой пары своя дата курса.
- `GET /currencies/convert?from=USD&to=RUB&amount=100.10` — `{amount,from,to,convertedAmount,rate,date,fetchedAt,provider}`.

Сумма неотрицательная, до 12 цифр в целой части и 6 после точки. Коды регистронезависимы.
Расчёт выполняется через `BigDecimal`: сумма × курс; округление `HALF_UP` по числу дробных знаков целевой валюты
(JPY — 0, RUB — 2, KWD — 3). Суммы и курсы в JSON возвращаются **строками** для сохранения точности.
При одинаковых валютах курс 1, `date=null`: внешнего курса для этого расчёта нет.

Каталог кэшируется на сутки, курсы каждой базовой валюты — на час, обновление при следующем запросе.
`fetchedAt` — момент получения данных, `date` — дата справочного курса, не момент сделки.
Это справочные курсы, а не предложение банка. Комиссии и банковские наценки не учитываются.
Конвертер не меняет расходы или долги: они остаются в исходных валютах.

Ошибки: 400 — некорректная сумма/валюта, 401 — JWT, 404 — нет курса пары, 503 — ошибка внешнего источника.
Таймаут подключения — 3 с, чтения — 8 с. JWT не отправляется провайдеру.
Тесты с локальным имитатором API: `./mvnw -pl currencyservice -am test`.

Общие Kafka и Redis: [запуск и настройки](../INFRASTRUCTURE.md).
