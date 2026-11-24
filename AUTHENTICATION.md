# Аутентификация

Регистрация и вход находятся в **userservice (8082)**. После входа один и тот же
Bearer JWT используется в userservice и **tripservice (8083)**.
Проверку токенов выполняет Spring Security OAuth2 Resource Server;
общая конфигурация вынесена в sharedlib.

## Настройка и запуск

Нужна Java 17+ и PostgreSQL для каждого сервиса, как и раньше. Дополнительно
установите **одинаковый JWT_SECRET в обоих процессах**. Значения по умолчанию нет;
если секрет отсутствует или короче 32 UTF-8 байт, приложение не запустится.
Пример генерации случайного секрета в PowerShell:

```powershell
$jwtSecretBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($jwtSecretBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtSecretBytes)
```

Передайте это значение обоим сервисам через окружение. Не генерируйте отдельный
секрет для каждого сервиса и не сохраняйте рабочий секрет в Git.
Остальные переменные:

| Переменная | Сервис | Значение |
| --- | --- | --- |
| POSTGRES_URL, POSTGRES_USERNAME, POSTGRES_PASSWORD | userservice | Подключение к БД пользователей |
| TRIP_POSTGRES_URL, TRIP_POSTGRES_USERNAME, TRIP_POSTGRES_PASSWORD | tripservice | Подключение к БД поездок |
| JWT_ISSUER | оба, одинаковое значение | По умолчанию travelapp |
| JWT_TTL_SECONDS | userservice | По умолчанию 3600 (1 час), диапазон 60–86400 |

Сборка и запуск из корня проекта:

```powershell
.\mvnw.cmd -pl userservice,tripservice -am install -DskipTests
.\mvnw.cmd -pl userservice spring-boot:run
# В другом терминале с тем же JWT_SECRET:
.\mvnw.cmd -pl tripservice spring-boot:run
```

## Регистрация

`POST http://localhost:8082/auth/register`, Content-Type: application/json:

```json
{
  "username": "alice",
  "password": "Compass!Trip42",
  "name": "Алиса"
}
```

Логин: 3–50 латинских букв, цифр или символов `_`, `.`, `-`,
без учёта регистра. Новый пароль: минимум 10 символов, строчная и прописная буквы, цифра и спецсимвол; не более 72 UTF-8 байт. Распространённые очевидные варианты отклоняются.
Имя обязательно, до 255 символов.

Ответ: **201 Created**, Location указывает на созданный профиль:

```json
{
  "accessToken": "<signed-jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": 1
}
```

Одновременно создаются профиль и настройки пользователя. Пароль хранится как
хэш BCrypt с cost=12; логин и хэш не включаются в JSON профиля и не могут быть
изменены через PUT /users/{id}. Повторная регистрация логина возвращает 409,
включая случай двух одновременных запросов (уникальное ограничение БД).
Старый POST /users закрыт: создание аккаунта выполняется через регистрацию.

## Вход

`POST http://localhost:8082/auth/login`, Content-Type: application/json:

```json
{"username":"alice","password":"Compass!Trip42"}
```

Ответ: **200 OK**, такой же формат, как при регистрации. Неверный логин
или пароль возвращает одинаковую ошибку 401.
Ответы с токенами имеют Cache-Control: no-store.

Пример использования в PowerShell:

```powershell
$loginBody = @{ username = 'alice'; password = 'Compass!Trip42' } | ConvertTo-Json
$auth = Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/auth/login' -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization = "Bearer $($auth.accessToken)" }
Invoke-RestMethod -Uri "http://localhost:8082/users/$($auth.userId)" -Headers $headers
Invoke-RestMethod -Uri 'http://localhost:8083/trips' -Headers $headers
```

Во всех запросах к поездкам, включая создание и управление участниками,
используйте `Authorization: Bearer <accessToken>`. Заголовок X-User-Id больше
не используется. Роли OWNER, MEMBER, READ_ONLY проверяются по ID из JWT.
Права на поездку всегда читаются из БД: смена роли или удаление участника
действуют сразу, без перевыпуска токена.

## Доступ и ошибки

Без JWT доступны POST /auth/register, /auth/login, /auth/refresh и /auth/logout. Для refresh/logout нужны сессионная cookie и заголовок X-TravelApp-Client: web. Профиль,
его удаление и настройки доступны только самому пользователю. Поиск
GET /users/by-telegram/{telegramId} также возвращает только собственный профиль;
он не является способом аутентификации через Telegram.

JWT подписывается HS256. Оба сервиса проверяют подпись, issuer, audience
travelapp-api, срок действия и положительный числовой subject (userId).
Для REST-доступа используется JWT. Для сайта добавлена отдельная HttpOnly cookie сессии на 7 дней; Basic Auth не используется.

| Код | Причина |
| --- | --- |
| 400 | Неверные входные данные |
| 401 | Нет токена, токен некорректен/просрочен или неверный пароль |
| 403 | Пользователь аутентифицирован, но прав недостаточно |
| 409 | Логин уже занят |

Формат ошибки: `{"message":"Invalid username or password","status":401}`.

## Существующие данные и срок действия

Hibernate добавляет в users nullable-колонки username и password_hash.
Старые профили сохраняются, но войти в них без учётных данных нельзя.
Регистрация создаёт новый ID и не присоединяет старый профиль или его поездки.
Для старых аккаунтов нужна доверенная миграция логинов и BCrypt-хэшей;
публичного присвоения пароля по чужому userId нет.

Access-токены действуют до истечения срока (по умолчанию 1 час). Сайт получает новую JWT через POST /auth/refresh по HttpOnly cookie на 7 дней. Выход POST /auth/logout отзывает refresh-сессию и удаляет cookie; уже выданные JWT действуют до собственного срока. Автоматический refresh не продлевает абсолютный 7-дневный срок сессии. В production задайте WEB_SESSION_SECURE=true и используйте HTTPS.

## Проверка

```powershell
.\mvnw.cmd -pl userservice,tripservice -am test
```

Тесты используют H2. Они проверяют регистрацию, вход, хранение BCrypt-хэша,
защиту учётных данных при редактировании профиля, доступ только к своим
профилям и настройкам, а также поездки с настоящими подписанными JWT,
просроченные и поддельные токены и невозможность подмены ID заголовком.

Документация используемого механизма:
[Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

Для сайта POST /auth/register и /auth/login с X-TravelApp-Client: web создают HttpOnly, SameSite=Strict cookie с Path=/api/users/auth. Настройки: WEB_SESSION_TTL_SECONDS=604800, WEB_SESSION_SECURE=false для локальной разработки (true с HTTPS). Только SHA-256 хэш случайного refresh-токена хранится в таблице refresh_session.

POST /users/directory с JWT и телом {"userIds":[1,2]} возвращает только userId и отображаемое имя. Используется для проверки ID и показа имён участников. Tripservice проверяет кандидата через userservice перед добавлением; адрес задаёт USER_SERVICE_URL.
