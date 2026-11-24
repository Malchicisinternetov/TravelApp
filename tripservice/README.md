# Поездки

POST /trips и PUT /trips/{id} принимают короткую форму без маршрута:

```json
{
  "name": "Отпуск",
  "description": "Летим через Стамбул",
  "destinationPoint": "Лондон"
}
```

Направление хранится отдельно от участков маршрута. legs можно не указывать
или передать пустой список: маршрут заполняется позже. Чтобы удалить все участки,
передайте legs: [] и сохраните destinationPoint.

Полная форма с legs продолжает поддерживаться. Если destinationPoint не указан,
он берётся из последнего участка. При пустом маршруте направление обязательно.
Ответ содержит destinationPoint и legs.

Остальные операции: GET /trips, GET /trips/{id}, DELETE /trips/{id},
GET/POST /trips/{id}/participants, PUT /trips/{id}/participants/{userId}/role,
DELETE /trips/{id}/participants/{userId}. Все запросы требуют Bearer JWT;
права OWNER, MEMBER и READ_ONLY проверяются сервисом.

Порт 8083. Переменные: TRIP_POSTGRES_URL, TRIP_POSTGRES_USERNAME,
TRIP_POSTGRES_PASSWORD и общий JWT_SECRET.

Общие Kafka и Redis: [запуск и настройки](../INFRASTRUCTURE.md).
