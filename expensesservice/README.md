# Расходы поездки

Порт: **8084**. Все запросы требуют `Authorization: Bearer <JWT>` из userservice.
Права и состав поездки проверяются через tripservice с тем же токеном.
OWNER и MEMBER добавляют расходы; READ_ONLY может просматривать историю и сводку.
Удалить запись может её автор или OWNER (автор с ролью READ_ONLY удалять не может).

Переменные окружения: `EXPENSES_POSTGRES_URL`, `EXPENSES_POSTGRES_USERNAME`,
`EXPENSES_POSTGRES_PASSWORD`, общий для сервисов `JWT_SECRET`.
Участники и права запрашиваются через Kafka с исходным JWT пользователя.
Таблицы создаются через Hibernate, как в остальных модулях.

```powershell
.\mvnw.cmd -pl expensesservice -am install -DskipTests
.\mvnw.cmd -pl expensesservice spring-boot:run
```

| Метод | Путь | Результат |
| --- | --- | --- |
| POST | /trips/{tripId}/expenses | Добавить расход, 201 |
| GET | /trips/{tripId}/expenses | История всей поездки |
| GET | /trips/{tripId}/expenses/me | Расходы, которые я оплатил или в которых есть моя доля |
| GET | /trips/{tripId}/expenses/summary | Суммы и долги всех участников |
| GET | /trips/{tripId}/expenses/summary/me | Мои суммы, кому должен я и кто должен мне |
| DELETE | /trips/{tripId}/expenses/{expenseId} | Удалить ошибочную запись, 204 |

Пример: участник 1 купил четыре билета за 12000 RUB. Добавление через
`POST /trips/1/expenses`, Content-Type: application/json:

```json
{
  "description": "Четыре билета",
  "spentOn": "2026-10-25",
  "paidBy": 1,
  "amount": 12000,
  "currency": "RUB",
  "participantIds": [1, 2, 3, 4]
}
```

Сумма делится поровну: по 3000 каждому. Если сумма не делится нацело,
остаток распределяется по минимальной денежной единице в порядке participantIds.
Сохранённые доли возвращаются в поле shares.

Короткий запрос для добавления расхода от своего имени сегодняшней датой:

```json
{"description": "Ужин", "amount": 1200, "currency": "RUB"}
```

Если paidBy не задан, плательщик берётся из JWT; если spentOn не задан, используется
сегодняшняя дата по EXPENSES_TIME_ZONE (по умолчанию Europe/Moscow).
Если нет ни participantIds, ни shares, сумма делится на всех текущих участников поездки.
Явный пустой participantIds не допускается.

Для неравных долей вместо participantIds передайте:

```json
"shares": [
  {"userId": 1, "amount": 2000},
  {"userId": 2, "amount": 10000}
]
```

Можно выбрать один способ распределения. Доли должны быть неотрицательными
и в сумме точно равняться amount. Плательщик может не входить в список долей.
Плательщик и получатели долей должны быть участниками поездки.

Сводка возвращает массив по валютам. В каждом элементе:

- `participants`: userId, paid (оплатил), share (пришлось на него),
  balance = paid − share. Положительный баланс — участнику должны, отрицательный — он должен.
- `debts`: fromUserId (должник), toUserId (кому должен), amount.

В примере каждый из участников 2, 3, 4 должен участнику 1 по 3000 RUB.
Встречные долги между одной парой участников взаимозачитываются.
Переноса долга через третьих лиц и учёта фактических возвратов денег пока нет.
Сводка /summary/me оставляет только мои итоги и долги с моим участием.
Удаление участника из поездки не меняет ранее сохранённые доли.

Валюты учитываются отдельно, без конвертации. Используются коды ISO 4217
с точностью 0–3 знака (например JPY, RUB/USD/EUR, KWD). Суммы хранятся в BigDecimal.
Точность определяется [Java Currency](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Currency.html#getDefaultFractionDigits()).
Ошибки данных — 400, отсутствие доступа — 403, поездка/расход не найдены — 404,
недоступность tripservice — 503.

Проверка: `./mvnw.cmd -pl expensesservice -am test`.
Тесты используют H2 и имитируют ответы tripservice.

Общие Kafka и Redis: [запуск и настройки](../INFRASTRUCTURE.md).

