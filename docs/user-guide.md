# Посібник користувача (End-User & API Consumer Guide)

**Система:** Beer Tracker Backend API (`v2.1.0`)  
**Інтерфейси взаємодії:** Вбудований **Swagger UI** (`http://localhost:8080/swagger-ui.html`) та HTTP REST API (`/api/v1/*`)  
**Термінологічна база:** [Глосарій предметної області (`spec/domain_glossary.md`)](file:///D:/uni/4-year/ПІС/beer-tracker/spec/domain_glossary.md)

---

## 1. Початок роботи та інтерфейс Swagger UI

Після запуску сервера (`mvn spring-boot:run` або `java -jar target/beer-tracker-1.0-SNAPSHOT.jar`) відкрийте у браузері:
- `http://localhost:8080/` (або `/swagger`, `/docs`, `/swagger-ui.html`).

У верхньому меню Swagger UI:
1. У випадаючому списку **Servers** оберіть **`http://localhost:8080/api/v1`** (або **`/api/v1`**).
2. Операції згруповані за розділами (тегами): `Authentication`, `Catalog`, `Versioning & Crowdsourcing`, `Moderation`, `Consumption`, `Analytics`, `Reviews`, `Wishlist & Collection`.
3. Для захищених операцій (позначених іконкою замка) натисніть кнопку **`Authorize`** у верхньому правому куті та вставте значення `access_token` (у форматі JWT без слова `Bearer` — Swagger UI додає префікс `Bearer ` автоматично).

---

## 2. Сценарій 1: Реєстрація, вхід та оновлення токенів (`Authentication`)

### 2.1. Реєстрація нового облікового запису
Викличте **`POST /api/v1/auth/register`** (`registerUser`):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "taster@example.com",
    "password": "StrongPassword123!",
    "timezone": "Europe/Kyiv"
  }'
```
- **Очікуваний результат (`201 Created`):** повертається `UserResponse` (`id`, `email`, `role: "USER"`, `timezone`, `created_at`). Поле пароля та хеш ніколи не повертаються.
- **Можливі помилки:**
  - `409 Conflict` (`EMAIL_ALREADY_EXISTS`) — email уже зареєстровано;
  - `422 Unprocessable Entity` (`UNPROCESSABLE_ENTITY`) — некоректний email, пароль коротший за 8 символів або невідомий IANA-пояс (напр., замість `Europe/Kyiv` передано довільний рядок).

### 2.2. Вхід та отримання пари токенів
Викличте **`POST /api/v1/auth/login`** (`loginUser`):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "taster@example.com", "password": "StrongPassword123!"}'
```
- **Очікуваний результат (`200 OK`):** `TokenResponse` з полями `access_token` (діє 900 секунд / 15 хв), `refresh_token` (діє 30 діб), `token_type: "Bearer"`, `expires_in: 900`.
- **Помилка:** `401 Unauthorized` (`INVALID_CREDENTIALS`) при хибному паролі або email.

### 2.3. Оновлення токенів (Single-Use Rotation)
Коли `access_token` закінчився, викличте **`POST /api/v1/auth/refresh`** (`refreshToken`):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "<REFRESH_TOKEN>"}'
```
- **Очікуваний результат (`200 OK`):** нова пара `access_token` і `refresh_token`. Попередній `refresh_token` одноразово анулюється — повторне його використання поверне `401 Unauthorized` (`INVALID_REFRESH_TOKEN`).

---

## 3. Сценарій 2: Пошук, фільтрація та реєстрація пива (`Catalog`)

### 3.1. Пошук і фільтрація за міцністю (ABV)
Публічний запит **`GET /api/v1/beers`** (`listBeers`) підтримує пошук за назвою/броварнею (`query`), діапазон міцності (`min_abv`, `max_abv` у межах `0.0..100.0`) та пагінацію (`page >= 0`, `1 <= size <= 100`):
```bash
curl -s "http://localhost:8080/api/v1/beers?query=Mova&min_abv=4.5&max_abv=8.0&page=0&size=20"
```
- **Очікуваний результат (`200 OK`):** `BeerPagedResponse` із полями `items`, `page`, `size`, `total_elements`, `total_pages`, `has_next`, `has_previous`.
- **Типові помилки (`400 Bad Request`):**
  - `INVALID_ABV_RANGE` — якщо `min_abv > max_abv` або значення поза `[0.0..100.0]`;
  - `INVALID_PAGE_SIZE` — якщо `size < 1` або `size > 100`.

### 3.2. Реєстрація нового продукту (`Beer` і `Packaging`)
Потрібен Bearer-токен. Викличте **`POST /api/v1/beers`** (`createBeer`):
```bash
curl -s -X POST http://localhost:8080/api/v1/beers \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mova Dark Stout",
    "brewery": "Mova Brewing Co.",
    "style": "Oatmeal Stout",
    "abv": 5.8,
    "beer_type": "PACKAGED",
    "country": "UA",
    "packagings": [
      { "volume_ml": 500, "package_type": "CAN", "barcode": "4820202600018" }
    ]
  }'
```
- **Очікуваний результат (`201 Created`):** повертається картка `BeerResponse` зі статусом `UNVERIFIED`, згенерованим UUID пива та кожного `Packaging`, і заголовком `Location: /api/v1/beers/{id}`.
- **Бізнес-обмеження:**
  - `beer_type = "DRAFT"` (**`Draft Beer`**, розливне пиво) допускає виключно `package_type = "DRAFT"` (інакше `422 INVALID_PACKAGE_TYPE_FOR_DRAFT`);
  - `beer_type = "PACKAGED"` забороняє `package_type = "DRAFT"` (`422 UNPROCESSABLE_ENTITY`);
  - `volume_ml` має бути в межах `50..10000` (`422 VOLUME_OUT_OF_BOUNDS`);
  - Повторна пара `(brewery, name)` або зайнятий `barcode` (8–14 цифр) відхиляються з `409 Conflict` (`DUPLICATE_BEER_NAME_CONFLICT` / `DUPLICATE_BARCODE_CONFLICT`).

### 3.3. Перегляд картки продукту (`verified_only`)
Викличте **`GET /api/v1/beers/{id}`** (`getBeerById`):
- Без параметрів або з `verified_only=false` картка повертає атрибути з урахуванням активної чернетки `BeerVersion` (якщо вона є).
- Із `verified_only=true` повертається базова версія каталогу без накладення неперевіреної чернетки.
- Для видалених (`Placeholder Beer`) або злитих (`MERGED`) позицій повертається `404 Not Found` (`BEER_NOT_FOUND`).

---

## 4. Сценарій 3: Пропозиція змін (`BeerVersion`) та крауд-голосування (`VersionVote`)

1. **Створити чернетку нової версії (`POST /api/v1/beers/{id}/versions`):**
   - Для одного `Beer` може існувати **не більше однієї** активної чернетки зі статусом `UNVERIFIED` (повторна спроба поверне `409 CONCURRENT_DRAFT_CONFLICT`).
2. **Переглянути чернетки для голосування (`GET /api/v1/beer-versions?status=UNVERIFIED`):**
   - Вимагає авторизації (`BearerAuth`).
   - Історію версій конкретного пива також можна переглянути через `GET /api/v1/beers/{id}/versions`.
3. **Проголосувати (`POST /api/v1/beer-versions/{id}/votes`):**
   - Передайте `{"vote_type": "CONFIRM"}` або `{"vote_type": "REJECT"}`.
   - Автор чернетки не може голосувати за власну версію (`403 SELF_VOTING_FORBIDDEN`).
   - Повторне голосування того самого користувача відхиляється (`409 DUPLICATE_VOTE_CONFLICT`).
   - При досягненні **5 голосів `CONFIRM`** версія автоматично стає `VERIFIED` і оновлює картку `Beer` у каталозі; при **5 голосах `REJECT`** — стає `REJECTED` (новий товар потрапляє в чергу модерації).

---

## 5. Сценарій 4: Облік споживання (`ConsumptionEntry`), `Wishlist` та `Collection`

### 5.1. Додавання пива до списку бажань (`Wishlist`)
- Додати: **`POST /api/v1/wishlist-items/{beer_id}`** $\rightarrow$ `201 Created` (повторне додавання — `409 WISHLIST_ITEM_ALREADY_EXISTS`).
- Переглянути список: **`GET /api/v1/wishlist-items?page=0&size=20`** $\rightarrow$ `200 OK`.
- Видалити вручну: **`DELETE /api/v1/wishlist-items/{beer_id}`** $\rightarrow$ `204 No Content` (або `404 WISHLIST_ITEM_NOT_FOUND`).

### 5.2. Фіксація факту споживання (Append-Only Log)
Викличте **`POST /api/v1/consumption-entries`** (`recordConsumptionEntry`):
```bash
curl -s -X POST http://localhost:8080/api/v1/consumption-entries \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "beer_id": "<BEER_UUID>",
    "packaging_id": "<PACKAGING_UUID>",
    "quantity": 2,
    "consumed_at": "2026-11-16T12:00:00Z"
  }'
```
- **Що відбувається атомарно в межах транзакції (`201 Created`):**
  1. Сервер сам обчислює `total_volume_ml = quantity * packaging_volume_ml`.
  2. Якщо пиво перебувало у `Wishlist` користувача, воно автоматично видаляється (`wishlist_autoremoved: true`).
  3. Пиво автоматично з'являється у віртуальній дегустаційній колекції (`collection_added: true`).
- **Валідація меж:** `quantity` у межах `1..100` (`422 INVALID_QUANTITY`), час `consumed_at` не в майбутньому (`422 CONSUMPTION_TIME_IN_FUTURE`), не старіший за 1 рік (`422 UNPROCESSABLE_ENTITY`) та не раніше моменту реєстрації облікового запису (`422 CONSUMPTION_TIME_BEFORE_REGISTRATION`).
- **Приватність та незмінність:**
  - **`GET /api/v1/consumption-entries`** і **`GET /api/v1/consumption-entries/{id}`** повертають лише власні записи користувача (спроба прочитати чужий ID повертає `404 CONSUMPTION_ENTRY_NOT_FOUND`).
  - Спроби змінити або видалити запис через **`PUT`**, **`PATCH`** або **`DELETE`** на `/api/v1/consumption-entries/{id}` завжди блокуються зі статусом **`405 Method Not Allowed`** (`METHOD_NOT_ALLOWED`).

### 5.3. Перегляд дегустаційної колекції (`Tasted Collection`)
Викличте **`GET /api/v1/collections/tasted?page=0&size=20`** $\rightarrow$ повертає унікальні продегустовані сорти з сумарним об'ємом, датами першого/останнього споживання та вашою активною оцінкою (`user_rating`).

---

## 6. Сценарій 5: Відгуки (`Review`) після споживання

Користувач може залишити **один активний відгук** на сорт пива виключно після того, як зафіксував принаймні один запис споживання цього пива:
- **Створення (`POST /api/v1/beers/{id}/reviews`):** `rating` від `1.0` до `5.0` з кроком `0.5` (`1.0, 1.5, ..., 5.0`), опціональний `review_text` (до 2000 символів).
  - Без попереднього запису споживання: `403 Forbidden` (`ERR_CONSUMPTION_REQUIRED`).
  - Якщо оцінка не кратна `0.5` або поза межами: `422 INVALID_RATING_VALUE`.
  - Якщо активний відгук уже існує: `409 REVIEW_ALREADY_EXISTS` (використовуйте `PUT`).
- **Оновлення (`PUT /api/v1/beers/{id}/reviews`):** ідемпотентно оновлює `rating` і `review_text` (`200 OK`).
- **Видалення (`DELETE /api/v1/beers/{id}/reviews`):** архівує відгук і перераховує рейтинг товару (`204 No Content`).

---

## 7. Сценарій 6: Персональна аналітика та стріки (`Analytics` & `Streak`)

- **`GET /api/v1/streaks`**: повертає `current_streak`, `longest_streak`, `last_consumed_date` та `is_active_today` за IANA-поясом користувача з межею доби (`Day Boundary`) `[00:00:00.000, 24:00:00.000)`.
- **`GET /api/v1/analytics/consumption`**: приймає опціональні ISO-дати `date`, `date_from`, `date_to` (`YYYY-MM-DD`) та повертає сумарний об'єм, кількість записів, стріки і 24-елементний погодинний профіль (`hourly_profile` для годин `0..23` з `volume_ml`, `entry_count`, `average_volume_ml`).

---

## 8. Сценарій 7: Дії модератора (`MODERATOR` only)

Публічна реєстрація (`POST /api/v1/auth/register`) завжди створює обліковий запис зі звичайною роллю `USER` (для запобігання самопризначенню прав), а при запуску база H2 не містить попередньо засіяних акаунтів модератора. Водночас сервер перевіряє роль `MODERATOR` за підписаним HMAC-SHA256 JWT (`JwtRequestAuthenticator`), тому перевірити режим модератора можна локально без змін коду:

1. **Згенеруйте тимчасовий HS256 JWT (TTL 15 хв) із `"role": "MODERATOR"`** (команда сумісна з PowerShell і Bash; якщо сервер запущено з власним `--security.jwt.secret`, замініть значення змінної `s`):
```bash
python -c "import base64,hmac,hashlib,json,time,uuid; s=b'beer-tracker-default-hmac-sha256-secret-key-2026-sdd-tdd'; b=lambda d: base64.urlsafe_b64encode(d).rstrip(b'=').decode(); t=int(time.time()); x=b(json.dumps({'alg':'HS256','typ':'JWT'},separators=(',',':')).encode())+'.'+b(json.dumps({'sub':str(uuid.uuid4()),'email':'moderator@beertracker.local','role':'MODERATOR','iat':t,'exp':t+900},separators=(',',':')).encode()); print(x+'.'+b(hmac.new(s,x.encode(),hashlib.sha256).digest()))"
```
2. **Авторизуйтеся у Swagger UI або `curl`:** у Swagger UI (`http://localhost:8080/swagger-ui.html`) натисніть **`Authorize`** і вставте згенерований токен (без префікса `Bearer`), або передайте його в заголовку `Authorization: Bearer <MODERATOR_TOKEN>`.

Усі операції нижче доступні лише для JWT-токенів із роллю `MODERATOR` (звичайний `USER` отримує `403 Forbidden`):
- **М'яке видалення (`DELETE /api/v1/beers/{id}`):** повертає `204 No Content`, переводить пиво у `Placeholder Beer` (`"Видалений продукт"`), приховує з каталогу і вішлістів, зберігаючи історичний літраж користувачів.
- **Злиття дублікатів (`POST /api/v1/merges`):** об'єднує `source_beer_id` $\rightarrow$ `target_beer_id` з міграцією пакувань, записів споживання та архівацією старіших дублікатних відгуків (`422 CANNOT_MERGE_BEER_INTO_ITSELF` при однакових ID).
- **Ручна верифікація/відхилення версії:** `POST /api/v1/beer-versions/{id}/verify` та `POST /api/v1/beer-versions/{id}/reject`.
- **Опрацювання черги модерації:**
  - `GET /api/v1/moderation-items/queue` — перелік відхилених (`REJECTED`) продуктів;
  - `POST /api/v1/moderation-items/{id}/decision` — рішення `APPROVE` (переведення в `VERIFIED`) або `PERMANENT_DELETE` (безповоротне видалення з каталогу та вішлістів).

---

## 9. Діагностика помилок та трасування (`X-Trace-Id`)

Будь-яка помилка (`4xx`/`5xx`) повертається в єдиній структурі `ErrorResponse`:
```json
{
  "code": "INVALID_ABV_RANGE",
  "message": "Parameter 'min_abv' cannot be greater than 'max_abv'",
  "details": [
    { "field": "min_abv", "issue": "min_abv (9.0) exceeds max_abv (4.0)" }
  ],
  "timestamp": "2026-11-16T14:25:00Z",
  "traceId": "c0a80123-7777-4444-9999-0123456789ab"
}
```
За значенням `traceId` (та заголовком відповіді `X-Trace-Id`) можна миттєво знайти відповідні події виконання запиту у структурованому журналі `logs/beer-tracker.json.log`.
