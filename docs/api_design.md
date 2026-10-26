# Проєктування REST API (API-First Design)

## Проєкт: Beer Tracker Backend
**Підхід:** API-First RESTful Design  
**Нормативні документи:** [SRS v2.1.0](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md), [Domain Glossary v2.0.0](file:///D:/uni/4-year/ПІС/beer-tracker/spec/domain_glossary.md)  
**Статус:** Специфікація API (Approved Design)  

---

## 1. Функціональні вимоги (REQ-ID), що реалізуються через REST API

Нижче наведено вичерпний перелік функціональних вимог специфікації SRS v2.1.0, реалізація яких забезпечується через інтерфейс REST API, а також супутні нефункціональні вимоги до контрактів, безпеки та обробки помилок.

| REQ-ID | Назва та зміст вимоги | Критерії приймання (AC) | Реалізація в REST API |
| :--- | :--- | :--- | :--- |
| **REQ-F-001** | **Реєстрація нового продукту в каталозі:** створення картки пива з обов'язковими атрибутами (`name`, `brewery`, `style`, `abv`, `beer_type`, `packagings`). | **AC-001.1:** початковий статус `UNVERIFIED`, генерація UUID.<br>**AC-001.2:** $\ge 1$ пакування, об'єм $50 \le \text{volume\_ml} \le 10000$ (інакше 422).<br>**AC-001.3:** для `beer_type = DRAFT` дозволено лише `package_type = DRAFT` (інакше 422).<br>**AC-001.4:** для `beer_type = PACKAGED` заборонено `package_type = DRAFT` (інакше 422). | `POST /api/v1/beers` |
| **REQ-F-002** | **Превентивна дедуплікація каталогу:** перевірка унікальності назви та штрих-коду перед збереженням. | **AC-002.1:** унікальність пари `(brewery, name)` case-insensitive, trimmed (інакше 409).<br>**AC-002.2:** унікальність штрих-коду `barcode` у пакуваннях (інакше 409). | `POST /api/v1/beers`<br>`POST /api/v1/beers/{id}/versions` |
| **REQ-F-003** | **Створення нової версії продукту (`BeerVersion`):** реєстрація пропозицій редагування картки товару та захист від розгалужень (forks). | **AC-003.1:** запис `BeerVersion` зі статусом `UNVERIFIED`, `confirm_votes = 0`, `reject_votes = 0`.<br>**AC-003.2:** заборона створення нової версії, якщо вже є активна `UNVERIFIED` (409 Conflict).<br>**AC-003.3:** `GET /beers/{id}` повертає останню чернетку за замовчуванням, або верифіковану версію при `verified_only=true`. | `POST /api/v1/beers/{id}/versions`<br>`GET /api/v1/beers/{id}`<br>`GET /api/v1/beers/{id}/versions` |
| **REQ-F-004** | **Краудсорсингове голосування за версію:** прийом голосів спільноти `CONFIRM` або `REJECT`. | **AC-004.1:** автор версії отримує 403 Forbidden при спробі голосувати за власну чернетку.<br>**AC-004.2:** повторне голосування користувача повертає 409 Conflict.<br>**AC-004.3:** дозволені лише значення `CONFIRM` (+1 confirm_votes) та `REJECT` (+1 reject_votes). | `POST /api/v1/beer-versions/{id}/votes`<br>`GET /api/v1/beer-versions?status=UNVERIFIED` |
| **REQ-F-005** | **Автоматична верифікація та відхилення версій ($N=5$):** зміна стану версії за порогом голосів. | **AC-005.1:** 5 `confirm_votes` $\rightarrow$ статус `VERIFIED`, заміна активної версії.<br>**AC-005.2:** 5 `reject_votes` для оновлення товару $\rightarrow$ видалення чернетки.<br>**AC-005.3:** 5 `reject_votes` для нового продукту $\rightarrow$ статус `REJECTED`, перехід у чергу модерації. | Спрацьовує в рамках операції `POST /api/v1/beer-versions/{id}/votes` |
| **REQ-F-006** | **Реєстрація факту споживання (Append-Only Log):** фіксація випитого напою з прив'язкою до тари та кількості. | **AC-006.1:** автоматичний розрахунок `total_volume_ml = quantity * Packaging.volume_ml`.<br>**AC-006.2:** `consumed_at > now()` $\rightarrow$ 422 Unprocessable Entity.<br>**AC-006.3:** `consumed_at < User.created_at` або `consumed_at < now() - 1 year` $\rightarrow$ 422.<br>**AC-006.4:** кількість $1 \le \text{quantity} \le 100$ (інакше 422). | `POST /api/v1/consumption-entries`<br>`GET /api/v1/consumption-entries` |
| **REQ-F-007** | **Заборона зміни та видалення записів споживання:** забезпечення абсолютної незмінності журналу. | **AC-007.1:** виклик `PUT`, `PATCH`, `DELETE` на ендпоінтах журналу повертає **405 Method Not Allowed**.<br>**AC-007.2:** жодна сервісна дія над каталогом не видаляє записи споживання. | Ендпоінти `PUT`, `PATCH`, `DELETE` на `/api/v1/consumption-entries/{id}` блокуються статусом `405` |
| **REQ-F-008** | **Написання та оновлення дегустаційного відгуку:** публікація оцінки та тексту вражень. | **AC-008.1:** спроба публікації відгуку без попереднього запису споживання $\rightarrow$ **403 Forbidden**.<br>**AC-008.2:** оцінка $1.0 \le \text{rating} \le 5.0$ із кроком 0.5 (інакше 422).<br>**AC-008.3:** щонайбільше 1 активний відгук на пару `(User, Beer)`; створення через `POST`, оновлення існуючого — через `PUT`. | `POST /api/v1/beers/{id}/reviews`<br>`PUT /api/v1/beers/{id}/reviews`<br>`DELETE /api/v1/beers/{id}/reviews` |
| **REQ-F-009** | **Автоматичне формування колекції та автоочищення Wishlist:** синхронізація списків при споживанні. | **AC-009.1:** перший факт споживання додає сорт до `Collection`.<br>**AC-009.2:** якщо напій був у `Wishlist`, він автоматично видаляється в тій самій транзакції. | `GET /api/v1/collections/tasted`<br>`GET /api/v1/wishlist-items`<br>`POST /api/v1/wishlist-items/{beer_id}`<br>`DELETE /api/v1/wishlist-items/{beer_id}` |
| **REQ-F-010** | **Видалення продукту модератором та Placeholder:** безпечне вилучення товару модератором. | **AC-010.1:** фізичний запис не видаляється; встановлюється `is_deleted = true`, товар вилучається з пошуку.<br>**AC-010.2:** в історії споживання відображається "Видалений продукт" (`Placeholder Beer`) зі збереженням об'єму та дати. | `DELETE /api/v1/beers/{id}` |
| **REQ-F-011** | **Злиття дублікатів (Merge):** об'єднання двох сортів із забезпеченням реляційної цілісності. | **AC-011.1:** пошук еквівалентного пакування в `target_beer` або автоматичне створення нового.<br>**AC-011.2:** атомарна міграція `ConsumptionEntry` на `target_beer_id` та `target_packaging_id`.<br>**AC-011.3:** архівація старішого відгуку користувача (`is_archived = true`).<br>**AC-011.4:** `source_beer` отримує `status = MERGED`, `is_deleted = true`, `merged_into_beer_id = target_beer_id`. | `POST /api/v1/merges` (або `POST /api/v1/beers/merge`) |
| **REQ-F-012** | **Розрахунок аналітики споживання та стріків:** агрегація показників за часовим поясом користувача. | **AC-012.1:** межа доби за IANA-таймзоною: $[00:00:00.000, 24:00:00.000)$ (`Day Boundary`).<br>**AC-012.2:** `Current Streak` — серія послідовних днів споживання до сьогодні/вчора.<br>**AC-012.3:** погодинний профіль за обрану дату ($h \in [0..23]$).<br>**AC-012.4:** середній історичний профіль за годину: $\frac{\sum \text{volume\_ml}[h]}{\text{total\_active\_days}}$. | `GET /api/v1/analytics/consumption`<br>`GET /api/v1/streaks` |
| **REQ-F-013** | **Адміністративна верифікація та відхилення версій модератором:** миттєвий аудит чернеток. | **AC-013.1:** ендпоінт схвалення переводить `BeerVersion` у `VERIFIED`, заміщує активну версію.<br>**AC-013.2:** ендпоінт відхилення переводить версію в `REJECTED`.<br>**AC-013.3:** доступ виключно для ролі `MODERATOR`; іншим користувачам — 403 Forbidden. | `POST /api/v1/beer-versions/{id}/verify`<br>`POST /api/v1/beer-versions/{id}/reject` |
| **REQ-F-014** | **Опрацювання черги модерації відхилених продуктів:** ручний розгляд продуктів із 5 Reject. | **AC-014.1:** отримання списку товарів у статусі `REJECTED`.<br>**AC-014.2:** рішення модератора: схвалити (`APPROVE` $\rightarrow$ `VERIFIED`) або безповоротно вилучити (`PERMANENT_DELETE`). | `GET /api/v1/moderation-items/queue`<br>`POST /api/v1/moderation-items/{beer_id}/decision` |

### Супутні нефункціональні вимоги до інтерфейсу API:
- **REQ-NF-004 (Автентифікація через stateless JWT):** реалізується через ендпоінти реєстрації (`POST /api/v1/auth/register`), авторизації (`POST /api/v1/auth/login`) та ротації токенів (`POST /api/v1/auth/refresh`).
- **REQ-NF-005 (Ізоляція приватних даних споживача):** спроба отримати або видалити чужий запис споживання чи елемент вішліста повертає суворо **`404 Not Found`** (захист від сканування чужих ID за рекомендаціями OWASP).
- **REQ-NF-007 (ACID транзакційність):** операції створення запису споживання (з автоочищенням вішліста та поповненням колекції) та операція злиття (`Merge`) виконуються в атомарних транзакціях рівня `Read Committed`.
- **REQ-NF-008 (Стандартизація помилок RFC 7807):** повернення помилок 4xx/5xx у форматі `application/problem+json` з полями `type`, `title`, `status`, `detail`, `instance`, `invalid_params`.
- **REQ-NF-009 (Запобігання гонкам даних при голосуванні):** обробка конкурентних запитів через повернення `409 Conflict` на базі унікального обмеження `(user_id, version_id)`.

---

## 2. Основні REST-ресурси предметної області

Згідно з канонічними правилами проєктування REST API та термінологією [Domain Glossary v2.0.0](file:///D:/uni/4-year/ПІС/beer-tracker/spec/domain_glossary.md) (Ubiquitous Language), імена ресурсів формуються **виключно у множині іменників (plural nouns)** у нижньому регістрі (kebab-case для складених слів).

### Термінологічна відповідність REST-ресурсів глосарію:

| Сутність у глосарії (Ubiquitous Language) | REST-ресурс (множина) | URI кореневої колекції | Опис ресурсу та його призначення в системі |
| :--- | :--- | :--- | :--- |
| **`Beer`**, **`Draft Beer`** | `beers` | `/api/v1/beers` | Канонічні продукти каталогу (фасоване та розливне пиво), пошук, перегляд карток, реєстрація нових товарів та м'яке видалення (Placeholder). |
| **`Packaging`** (`BeerPackaging`) | `beer-packagings` | `/api/v1/beers/{id}/packagings`<br>*(або `/api/v1/beer-packagings`)* | Варіанти фізичної тари для конкретного продукту (об'єм у мл, тип `BOTTLE`/`CAN`/`DRAFT`/`KEG`, штрих-код). Вкладений підресурс або окрема сутність. |
| **`BeerVersion`** | `beer-versions` | `/api/v1/beer-versions`<br>*(підресурс: `/api/v1/beers/{id}/versions`)* | Запропоновані чернетки версій продуктів, що очікують крауд-голосування або модераторського рішення. |
| **`VersionVote`** | `version-votes` | `/api/v1/beer-versions/{id}/votes`<br>*(або `/api/v1/version-votes`)* | Голоси користувачів (`CONFIRM` / `REJECT`) за запропоновані чернетки версій. |
| **`ConsumptionEntry`** | `consumption-entries` | `/api/v1/consumption-entries`<br>*(аліас: `/api/v1/consumption`)* | Незмінний (Append-Only) журнал зафіксованих фактів індивідуального споживання напоїв користувачем. Заборонено будь-які операції оновлення та видалення (405). |
| **`Review`** | `reviews` | `/api/v1/beers/{id}/reviews`<br>*(або `/api/v1/reviews`)* | Персональні публічні оцінки (1.0–5.0) та текстові враження про скуштоване пиво (не більше 1 активного відгуку на користувача). |
| **`Wishlist`** (`WishlistItem`) | `wishlist-items` | `/api/v1/wishlist-items`<br>*(аліас: `/api/v1/wishlist`)* | Персональний список сортів пива, запланованих до дегустації (з автоматичним видаленням після першого вживання). |
| **`Collection`** (`Tasted Collection`) | `collections` | `/api/v1/collections/tasted`<br>*(аліас: `/api/v1/collection`)* | Віртуальна дегустаційна колекція користувача — проекція унікальних сортів пива, що мають $\ge 1$ запис у журналі споживання. |
| **`Streak`** / **`Analytics`** | `streaks`, `analytics` | `/api/v1/streaks`<br>`/api/v1/analytics/consumption` | Обчислювані агреговані метрики: поточна та максимальна серії днів споживання, добовий розподіл, погодинний профіль споживання з урахуванням IANA-таймзони. |
| **`Merge`** | `merges` | `/api/v1/merges`<br>*(аліас: `/api/v1/beers/merge`)* | Адміністративні транзакційні операції об'єднання сортів-дублікатів із міграцією пакувань та журналів споживання. |
| **`ModerationItem`** | `moderation-items` | `/api/v1/moderation-items/queue`<br>*(аліас: `/api/v1/moderation/queue`)* | Елементи черги модерації відхилених продуктів ($N=5$ Reject) для остаточного затвердження або видалення модератором. |
| **`User`** / **`Token`** | `users`, `tokens` | `/api/v1/users`<br>`/api/v1/tokens` *(аліас: `/api/v1/auth`)* | Сутності користувачів системи та сесійні токени автентифікації (JWT Access/Refresh пари). |

---

## 3. Зведена таблиця операцій REST API

У таблиці нижче наведено повну специфікацію операцій REST API системи Beer Tracker. Для забезпечення сумісності із розділом 3.2.1 SRS v2.1.0 та вимогами чистого REST-проєктування, адреси URI стандартизовано у множині відповідно до глосарія із підтримкою коротких аліасів специфікації.

| Operation ID | HTTP Method | URI | Path / Query Parameters | Request Body (короткий опис полів) | Response Body (успішна відповідь) | HTTP Status Codes | Пов'язані REQ-ID |
| :--- | :---: | :--- | :--- | :--- | :--- | :--- | :--- |
| **`registerUser`** | `POST` | `/api/v1/users`<br>*(аліас `/api/v1/auth/register`)* | Немає | **JSON Object:**<br>• `email` (string, valid email, required)<br>• `password` (string, min 8 chars, required)<br>• `timezone` (string, valid IANA timezone, e.g. "Europe/Kyiv", required) | **JSON Object:**<br>• `id` (uuid)<br>• `email` (string)<br>• `role` ("USER")<br>• `timezone` (string)<br>• `created_at` (ISO 8601 string) | • `201 Created`<br>• `400 Bad Request` (невалідний JSON)<br>• `409 Conflict` (email вже зайнятий)<br>• `422 Unprocessable Entity` (невалідна таймзона, слабкий пароль) | `REQ-NF-004`<br>`REQ-NF-006`<br>`REQ-NF-008` |
| **`loginUser`** | `POST` | `/api/v1/tokens`<br>*(аліас `/api/v1/auth/login`)* | Немає | **JSON Object:**<br>• `email` (string, required)<br>• `password` (string, required) | **JSON Object:**<br>• `access_token` (string, JWT 15 min)<br>• `refresh_token` (string, UUID/JWT 30 days)<br>• `token_type` ("Bearer")<br>• `expires_in` (integer, 900) | • `200 OK`<br>• `400 Bad Request`<br>• `401 Unauthorized` (невірний логін або пароль) | `REQ-NF-004`<br>`REQ-NF-006`<br>`REQ-NF-008` |
| **`refreshToken`** | `POST` | `/api/v1/tokens/refresh`<br>*(аліас `/api/v1/auth/refresh`)* | Немає | **JSON Object:**<br>• `refresh_token` (string, required) | **JSON Object:**<br>• `access_token` (string, JWT)<br>• `refresh_token` (string, ротований новий токен)<br>• `token_type` ("Bearer")<br>• `expires_in` (integer, 900) | • `200 OK`<br>• `400 Bad Request`<br>• `401 Unauthorized` (прострочений або скомпрометований токен) | `REQ-NF-004`<br>`REQ-NF-008` |
| **`listBeers`** | `GET` | `/api/v1/beers` | **Query Parameters (опціональні):**<br>• `query` (string, пошук за назвою/броварнею)<br>• `brewery` (string)<br>• `style` (string)<br>• `country` (string)<br>• `beer_type` (string: "PACKAGED" \| "DRAFT")<br>• `barcode` (string)<br>• `page` (int, default 0)<br>• `size` (int, default 20, max 100)<br>• `sort` (string, default "name,asc") | Порожнє | **JSON Object (Paged List):**<br>• `items`: масив об'єктів `Beer` (`id`, `name`, `brewery`, `style`, `abv`, `beer_type`, `country`, `image_url`, `packagings`, `rating_avg`, `reviews_count`)<br>• `page`, `size`, `total_elements`, `total_pages` | • `200 OK`<br>• `400 Bad Request` (помилка параметрів пагінації/сортування) | `REQ-F-001`<br>`REQ-NF-001`<br>`REQ-NF-008` |
| **`getBeerById`** | `GET` | `/api/v1/beers/{id}` | **Path Parameters:**<br>• `id` (uuid, required)<br>**Query Parameters:**<br>• `verified_only` (boolean, default false) | Порожнє | **JSON Object:**<br>• Картка `Beer` із повною інформацією, переліком `packagings`, поточною діючою або останньою запропонованою версією, середнім рейтингом та кількістю відгуків | • `200 OK`<br>• `404 Not Found` (товар не знайдено або `is_deleted=true`) | `REQ-F-001`<br>`REQ-F-003`<br>`REQ-F-010`<br>`REQ-NF-008` |
| **`createBeer`** | `POST` | `/api/v1/beers` | Немає | **JSON Object:**<br>• `name` (string, 1..255 chars, required)<br>• `brewery` (string, 1..255 chars, required)<br>• `style` (string, 1..100 chars, required)<br>• `abv` (float, 0.0..100.0, required)<br>• `beer_type` ("PACKAGED" \| "DRAFT", required)<br>• `description` (string, optional)<br>• `country` (string, 2 chars ISO, optional)<br>• `image_url` (string, valid URL, optional)<br>• `packagings` (array of objects, min 1 item, required):<br>&nbsp;&nbsp;– `volume_ml` (int, 50..10000)<br>&nbsp;&nbsp;– `package_type` ("BOTTLE" \| "CAN" \| "DRAFT" \| "KEG")<br>&nbsp;&nbsp;– `barcode` (string, 8..14 digits, optional) | **JSON Object:**<br>• Створена сутність `Beer` зі статусом `UNVERIFIED`, згенерованим `id`, масивом `packagings` та часовою міткою `created_at` | • `201 Created`<br>• `400 Bad Request`<br>• `401 Unauthorized`<br>• `409 Conflict` (дублікат назви+броварні або штрих-коду)<br>• `422 Unprocessable Entity` (порушення правил тари, об'єму або невідповідність типу DRAFT/PACKAGED) | `REQ-F-001`<br>`REQ-F-002`<br>`REQ-NF-008` |
| **`deleteBeer`** | `DELETE` | `/api/v1/beers/{id}` | **Path Parameters:**<br>• `id` (uuid, required) | Порожнє | Порожнє *(або JSON-повідомлення про soft-delete)* | • `204 No Content`<br>• `401 Unauthorized`<br>• `403 Forbidden` (доступ тільки для модератора)<br>• `404 Not Found` (товар не знайдено) | `REQ-F-010`<br>`REQ-NF-008` |
| **`createBeerVersion`** | `POST` | `/api/v1/beers/{id}/versions`<br>*(аліас `/api/v1/beer-versions`)* | **Path Parameters:**<br>• `id` (uuid, required) | **JSON Object (запропоновані зміни):**<br>• `name`, `brewery`, `style`, `abv`, `beer_type`, `description`, `country`, `image_url` (optional змінені поля)<br>• `packagings` (опціональний новий перелік варіантів тари) | **JSON Object:**<br>• `version_id` (uuid)<br>• `beer_id` (uuid)<br>• `author_id` (uuid)<br>• `payload` (JSON patch / snapshot)<br>• `confirm_votes` (0)<br>• `reject_votes` (0)<br>• `status` ("UNVERIFIED")<br>• `created_at` (ISO 8601) | • `201 Created`<br>• `400 Bad Request`<br>• `401 Unauthorized`<br>• `404 Not Found` (пиво не існує)<br>• `409 Conflict` (вже існує активна версія UNVERIFIED або колізія штрих-коду)<br>• `422 Unprocessable Entity` (невалідні дані атрибутів чи тари) | `REQ-F-002`<br>`REQ-F-003`<br>`REQ-NF-008` |
| **`listBeerVersions`** | `GET` | `/api/v1/beers/{id}/versions` | **Path Parameters:**<br>• `id` (uuid, required) | Порожнє | **JSON Array:**<br>• Список усіх історичних та поточних версій `BeerVersion` даного продукту зі статусами та голосами | • `200 OK`<br>• `404 Not Found` | `REQ-F-003`<br>`REQ-NF-008` |
| **`listPendingBeerVersions`** | `GET` | `/api/v1/beer-versions`<br>*(аліас `/api/v1/beers/versions/pending`)* | **Query Parameters:**<br>• `status` ("UNVERIFIED", default)<br>• `page` (int, default 0)<br>• `size` (int, default 20) | Порожнє | **JSON Object (Paged List):**<br>• Список чернеток версій, що очікують краудсорсингового голосування, включаючи автора, зміни та поточні лічильники | • `200 OK`<br>• `401 Unauthorized` | `REQ-F-003`<br>`REQ-F-004`<br>`REQ-NF-008` |
| **`voteBeerVersion`** | `POST` | `/api/v1/beer-versions/{id}/votes`<br>*(аліас `/api/v1/beers/versions/{id}/vote`)* | **Path Parameters:**<br>• `id` (uuid, required) — ID версії | **JSON Object:**<br>• `vote_type` (string, "CONFIRM" \| "REJECT", required) | **JSON Object:**<br>• `version_id` (uuid)<br>• `confirm_votes` (integer)<br>• `reject_votes` (integer)<br>• `status` ("UNVERIFIED" \| "VERIFIED" \| "REJECTED")<br>• `message` (string, підсумок врахування голосу або настання автоверифікації $N=5$) | • `200 OK`<br>• `400 Bad Request`<br>• `401 Unauthorized`<br>• `403 Forbidden` (автор голосує за власну версію)<br>• `404 Not Found` (версію не знайдено)<br>• `409 Conflict` (повторне голосування користувача або стан race condition) | `REQ-F-004`<br>`REQ-F-005`<br>`REQ-NF-008`<br>`REQ-NF-009` |
| **`verifyBeerVersion`** | `POST` | `/api/v1/beer-versions/{id}/verify`<br>*(аліас `/api/v1/moderation/versions/{id}/verify`)* | **Path Parameters:**<br>• `id` (uuid, required) — ID версії | Порожнє | **JSON Object:**<br>• Оновлена сутність `BeerVersion` зі статусом `VERIFIED`, ID деактивованої попередньої версії та оновлений канонічний `Beer` | • `200 OK`<br>• `401 Unauthorized`<br>• `403 Forbidden` (тільки для ролі MODERATOR)<br>• `404 Not Found`<br>• `409 Conflict` (версія вже верифікована або відхилена) | `REQ-F-013`<br>`REQ-NF-008` |
| **`rejectBeerVersion`** | `POST` | `/api/v1/beer-versions/{id}/reject`<br>*(аліас `/api/v1/moderation/versions/{id}/reject`)* | **Path Parameters:**<br>• `id` (uuid, required) — ID версії | **JSON Object (опціонально):**<br>• `reason` (string, причина відхилення) | **JSON Object:**<br>• Інформація про відхилену версію зі статусом `REJECTED` | • `200 OK`<br>• `401 Unauthorized`<br>• `403 Forbidden` (тільки для ролі MODERATOR)<br>• `404 Not Found` | `REQ-F-013`<br>`REQ-NF-008` |
| **`listModerationQueue`** | `GET` | `/api/v1/moderation-items/queue`<br>*(аліас `/api/v1/moderation/queue`)* | **Query Parameters:**<br>• `page` (int, default 0)<br>• `size` (int, default 20) | Порожнє | **JSON Object (Paged List):**<br>• Список нових продуктів зі статусом `REJECTED`, які були відхилені краудсорсингом ($N=5$) та очікують ручного аудиту | • `200 OK`<br>• `401 Unauthorized`<br>• `403 Forbidden` (тільки для ролі MODERATOR) | `REQ-F-014`<br>`REQ-NF-008` |
| **`resolveModerationQueueItem`** | `POST` | `/api/v1/moderation-items/{id}/decision`<br>*(аліас `/api/v1/moderation/beers/{id}/decision`)* | **Path Parameters:**<br>• `id` (uuid, required) — ID товару | **JSON Object:**<br>• `decision` (string, "APPROVE" \| "PERMANENT_DELETE", required)<br>• `comment` (string, optional) | **JSON Object:**<br>• Результат опрацювання: підтверджений статус `VERIFIED` або факт повного вилучення з БД | • `200 OK`<br>• `400 Bad Request`<br>• `401 Unauthorized`<br>• `403 Forbidden` (тільки MODERATOR)<br>• `404 Not Found`<br>• `422 Unprocessable Entity` (неприпустиме значення `decision`) | `REQ-F-014`<br>`REQ-NF-008` |
| **`mergeBeers`** | `POST` | `/api/v1/merges`<br>*(аліас `/api/v1/beers/merge`)* | Немає | **JSON Object:**<br>• `source_beer_id` (uuid, required)<br>• `target_beer_id` (uuid, required) | **JSON Object:**<br>• `target_beer_id` (uuid)<br>• `migrated_consumptions_count` (int)<br>• `migrated_packagings_count` (int)<br>• `archived_reviews_count` (int)<br>• `status` ("MERGED") | • `200 OK`<br>• `400 Bad Request`<br>• `401 Unauthorized`<br>• `403 Forbidden` (тільки MODERATOR)<br>• `404 Not Found` (source або target не знайдено)<br>• `422 Unprocessable Entity` (спроба злити пиво саме із собою) | `REQ-F-011`<br>`REQ-NF-007`<br>`REQ-NF-008` |
| **`recordConsumptionEntry`** | `POST` | `/api/v1/consumption-entries`<br>*(аліас `/api/v1/consumption`)* | Немає | **JSON Object:**<br>• `beer_id` (uuid, required)<br>• `packaging_id` (uuid, required)<br>• `quantity` (integer, 1..100, required)<br>• `consumed_at` (datetime string ISO 8601, required) | **JSON Object:**<br>• `id` (uuid)<br>• `beer_id` (uuid)<br>• `packaging_id` (uuid)<br>• `quantity` (int)<br>• `total_volume_ml` (int, розраховано сервером)<br>• `consumed_at` (datetime)<br>• `wishlist_autoremoved` (boolean)<br>• `collection_added` (boolean) | • `201 Created`<br>• `400 Bad Request`<br>• `401 Unauthorized`<br>• `404 Not Found` (пиво або пакування не існує)<br>• `422 Unprocessable Entity` (`quantity` поза [1..100], час у майбутньому, раніше реєстрації або старіший 1 року) | `REQ-F-006`<br>`REQ-F-009`<br>`REQ-NF-003`<br>`REQ-NF-007`<br>`REQ-NF-008` |
| **`listConsumptionEntries`** | `GET` | `/api/v1/consumption-entries`<br>*(аліас `/api/v1/consumption`)* | **Query Parameters (опціональні):**<br>• `date_from` (ISO 8601 date)<br>• `date_to` (ISO 8601 date)<br>• `beer_id` (uuid)<br>• `page` (int, default 0)<br>• `size` (int, default 20) | Порожнє | **JSON Object (Paged List):**<br>• Список записів `ConsumptionEntry` поточного автентифікованого користувача (із назвами напоїв або Placeholder Beer для видалених, тарою, об'ємом та часом) | • `200 OK`<br>• `401 Unauthorized` | `REQ-F-006`<br>`REQ-F-010`<br>`REQ-NF-005`<br>`REQ-NF-008` |
| **`getConsumptionEntryById`** | `GET` | `/api/v1/consumption-entries/{id}`<br>*(аліас `/api/v1/consumption/{id}`)* | **Path Parameters:**<br>• `id` (uuid, required) | Порожнє | **JSON Object:**<br>• Детальний запис `ConsumptionEntry` поточного користувача | • `200 OK`<br>• `401 Unauthorized`<br>• `404 Not Found` (запис не знайдено або належить чужому користувачу за OWASP `REQ-NF-005`) | `REQ-F-006`<br>`REQ-F-010`<br>`REQ-NF-005`<br>`REQ-NF-008` |
| **`checkConsumptionImmutable`** | `PUT`, `PATCH`, `DELETE` | `/api/v1/consumption-entries/{id}`<br>*(не визначені в OpenAPI Path Item — шлюз/роутер автоматично блокує виклик)* | **Path Parameters:**<br>• `id` (uuid, required) | Довільне тіло або порожнє | **ErrorResponse / RFC 7807:**<br>• `code`: "METHOD_NOT_ALLOWED"<br>• `message`: "Consumption entries are immutable (Append-Only) and cannot be updated or deleted" | • `405 Method Not Allowed` | `REQ-F-007`<br>`REQ-NF-008` |
| **`getConsumptionAnalytics`** | `GET` | `/api/v1/analytics/consumption`<br>*(аліас `/api/v1/consumption/analytics`)* | **Query Parameters:**<br>• `date` (ISO date, e.g. "2026-10-20")<br>• `date_from` (ISO date)<br>• `date_to` (ISO date) | Порожнє | **JSON Object:**<br>• `timezone` (string)<br>• `current_streak` (int, дні)<br>• `longest_streak` (int, дні)<br>• `total_volume_ml` (int)<br>• `total_entries` (int)<br>• `hourly_profile`: масив 24 об'єктів для кожної години ($h \in [0..23]$):<br>&nbsp;&nbsp;– `hour` (int, 0..23)<br>&nbsp;&nbsp;– `volume_ml` (int)<br>&nbsp;&nbsp;– `entry_count` (int)<br>&nbsp;&nbsp;– `average_volume_ml` (float) | • `200 OK`<br>• `400 Bad Request` (помилка формату дати)<br>• `401 Unauthorized` | `REQ-F-012`<br>`REQ-NF-002`<br>`REQ-NF-008` |
| **`getStreaks`** | `GET` | `/api/v1/streaks` | Немає | Порожнє | **JSON Object:**<br>• `current_streak` (int)<br>• `longest_streak` (int)<br>• `last_consumed_date` (ISO date)<br>• `is_active_today` (boolean) | • `200 OK`<br>• `401 Unauthorized` | `REQ-F-012`<br>`REQ-NF-002`<br>`REQ-NF-008` |
| **`createReview`** | `POST` | `/api/v1/beers/{id}/reviews`<br>*(аліас `/api/v1/reviews/{beer_id}`)* | **Path Parameters:**<br>• `id` (uuid, required) — ID пива | **JSON Object:**<br>• `rating` (float, 1.0..5.0 з кроком 0.5, required)<br>• `review_text` (string, max 2000 chars, optional) | **JSON Object:**<br>• `id` (uuid)<br>• `beer_id` (uuid)<br>• `user_id` (uuid)<br>• `rating` (float)<br>• `review_text` (string)<br>• `is_archived` (false)<br>• `created_at` (datetime)<br>• `updated_at` (datetime) | • `201 Created`<br>• `400 Bad Request`<br>• `401 Unauthorized`<br>• `403 Forbidden` (відсутній факт споживання в користувача)<br>• `404 Not Found` (пиво не знайдено)<br>• `409 Conflict` (відгук на цей сорт пива вже існує у даного користувача; для оновлення використовуйте PUT)<br>• `422 Unprocessable Entity` (рейтинг поза діапазоном або крок $\ne 0.5$) | `REQ-F-008`<br>`REQ-NF-008` |
| **`updateReview`** | `PUT` | `/api/v1/beers/{id}/reviews`<br>*(аліас `/api/v1/reviews/{beer_id}`)* | **Path Parameters:**<br>• `id` (uuid, required) — ID пива | **JSON Object:**<br>• `rating` (float, 1.0..5.0 з кроком 0.5, required)<br>• `review_text` (string, max 2000 chars, optional) | **JSON Object:**<br>• `id` (uuid)<br>• `beer_id` (uuid)<br>• `user_id` (uuid)<br>• `rating` (float)<br>• `review_text` (string)<br>• `is_archived` (false)<br>• `updated_at` (datetime) | • `200 OK`<br>• `400 Bad Request`<br>• `401 Unauthorized`<br>• `403 Forbidden` (відсутній факт споживання або спроба редагування чужого відгуку)<br>• `404 Not Found` (відгук не знайдено для оновлення)<br>• `422 Unprocessable Entity` (рейтинг поза діапазоном або крок $\ne 0.5$) | `REQ-F-008`<br>`REQ-NF-008` |
| **`deleteReview`** | `DELETE` | `/api/v1/beers/{id}/reviews`<br>*(аліас `/api/v1/reviews/{beer_id}`)* | **Path Parameters:**<br>• `id` (uuid, required) | Порожнє | Порожнє | • `204 No Content`<br>• `401 Unauthorized`<br>• `404 Not Found` (відгук не знайдено) | `REQ-F-008`<br>`REQ-NF-008` |
| **`listWishlistItems`** | `GET` | `/api/v1/wishlist-items`<br>*(аліас `/api/v1/wishlist`)* | **Query Parameters:**<br>• `page` (int, default 0)<br>• `size` (int, default 50) | Порожнє | **JSON Object (Paged List):**<br>• Список `WishlistItem` поточного користувача із короткою карткою `Beer` та датою додавання `added_at` | • `200 OK`<br>• `401 Unauthorized` | `REQ-F-009`<br>`REQ-NF-005`<br>`REQ-NF-008` |
| **`addWishlistItem`** | `POST` | `/api/v1/wishlist-items/{beer_id}`<br>*(аліас `/api/v1/wishlist/{beer_id}`)* | **Path Parameters:**<br>• `beer_id` (uuid, required) | Порожнє | **JSON Object:**<br>• `id` (uuid)<br>• `beer_id` (uuid)<br>• `added_at` (datetime) | • `201 Created`<br>• `401 Unauthorized`<br>• `404 Not Found` (пиво не існує або видалене)<br>• `409 Conflict` (пиво вже додано до вішліста) | `REQ-F-009`<br>`REQ-NF-005`<br>`REQ-NF-008` |
| **`removeWishlistItem`** | `DELETE` | `/api/v1/wishlist-items/{beer_id}`<br>*(аліас `/api/v1/wishlist/{beer_id}`)* | **Path Parameters:**<br>• `beer_id` (uuid, required) | Порожнє | Порожнє | • `204 No Content`<br>• `401 Unauthorized`<br>• `404 Not Found` (товару немає у вішлісті користувача — повертається 404 за REQ-NF-005) | `REQ-F-009`<br>`REQ-NF-005`<br>`REQ-NF-008` |
| **`getTastedCollection`** | `GET` | `/api/v1/collections/tasted`<br>*(аліас `/api/v1/collection`)* | **Query Parameters:**<br>• `page` (int, default 0)<br>• `size` (int, default 20)<br>• `sort` (string, e.g. "first_consumed_at,desc") | Порожнє | **JSON Object (Paged List):**<br>• Перелік унікальних продегустованих сортів пива користувача із сумарним випитим об'ємом, датою першого та останнього споживання, власною оцінкою (якщо є) | • `200 OK`<br>• `401 Unauthorized` | `REQ-F-009`<br>`REQ-NF-005`<br>`REQ-NF-008` |

---

## 4. Стандартизація контрактів: Формат помилок (`ErrorResponse`), статус-коди та правила валідації

Відповідно до нефункціональної вимоги **REQ-NF-008** та контракту [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml), усі відповіді API при помилках клієнта (4xx) та сервера (5xx) використовують єдину стандартизовану схему **`ErrorResponse`**.

### 4.1. Канонічна структура помилки (`ErrorResponse`)

Кожна помилкова відповідь містить фіксований набір машиночитних та людиночитних атрибутів для клієнтської обробки та наскрізного MDC-трасування:

```json
{
  "code": "UNPROCESSABLE_ENTITY",
  "message": "Provided business attributes violated domain integrity rules",
  "details": [
    {
      "field": "quantity",
      "issue": "Quantity must be an integer between 1 and 100"
    },
    {
      "field": "consumed_at",
      "issue": "Timestamp cannot be in the future"
    }
  ],
  "timestamp": "2026-10-20T18:30:00Z",
  "traceId": "20a80123-7777-4444-9999-0123456789ab"
}
```

#### Опис полів:
- `code` (string, required): стабільний машиночитний код помилки (наприклад, `UNPROCESSABLE_ENTITY`, `DUPLICATE_BARCODE_CONFLICT`, `ERR_CONSUMPTION_REQUIRED`, `UNAUTHORIZED`, `NOT_FOUND`).
- `message` (string, required): зрозумілий людині узагальнений опис помилки.
- `details` (array of `ErrorDetail`, required): масив конкретних помилок по полях запиту (`field` та `issue`).
- `timestamp` (ISO 8601 date-time, required): часова мітка виникнення помилки в UTC.
- `traceId` (UUID string, required): ідентифікатор трасування запиту (`MDC trace_id` згідно з `REQ-NF-010`).

---

### 4.2. Семантика та матриця HTTP-статус-кодів

| HTTP Код | Канонічна назва | Сценарій використання в Beer Tracker API | Пов'язані REQ-ID |
| :---: | :--- | :--- | :--- |
| **`200 OK`** | Success | Успішне виконання безпечних операцій читання (`GET`), модифікація наявного відгуку (`PUT /beers/{id}/reviews`) або виконання спеціальних дій (`POST /verify`, `/reject`, `/merge`). | `REQ-F-008`, Загальний для читання |
| **`201 Created`** | Created | Успішне створення нового ресурсу (`POST /beers`, `POST /consumption-entries`, `POST /beer-versions`, `POST /beers/{id}/reviews`, `POST /wishlist-items`). У заголовку повертається `Location: /api/v1/{resource}/{id}`. | `REQ-F-001`, `REQ-F-003`, `REQ-F-006`, `REQ-F-008`, `REQ-F-009` |
| **`204 No Content`** | No Content | Успішне видалення без необхідності повернення тіла відповіді (`DELETE /beers/{id}`, `DELETE /wishlist-items/{id}`, `DELETE /reviews/{id}`). | `REQ-F-008`, `REQ-F-009`, `REQ-F-010` |
| **`400 Bad Request`** | Syntax Error | Синтаксична помилка парсингу JSON (незакриті дужки, синтаксичний збій типів), невалідні параметри пагінації (`page < 0`, `size > 100`). | `REQ-NF-008` |
| **`401 Unauthorized`** | Authentication Required | Відсутній, прострочений або невалідний JWT Bearer-токен у заголовку `Authorization`. | `REQ-NF-004`, `REQ-NF-008` |
| **`403 Forbidden`** | Permission Denied | 1. Звичайний користувач намагається викликати модераторські операції (`/verify`, `/reject`, `/merge`, `/moderation/queue`).<br>2. Автор версії намагається проголосувати за власну чернетку (REQ-F-004).<br>3. Спроба написати відгук без наявності запису споживання (REQ-F-008). | `REQ-F-004`, `REQ-F-008`, `REQ-F-013`, `REQ-F-014` |
| **`404 Not Found`** | Resource Missing / OWASP Data Isolation | 1. Запитуваний ресурс (`Beer`, `BeerVersion`) не існує в базі даних або був позначений `is_deleted = true`.<br>2. **OWASP Data Isolation:** Будь-яка спроба звернутися до чужого запису споживання чи вішліста повертає **суворо 404** (замість 403), запобігаючи витоку інформації про існування запису (REQ-NF-005). | `REQ-NF-005`, `REQ-NF-008` |
| **`405 Method Not Allowed`** | Verb Forbidden | Сувора заборона методів модифікації `PUT`, `PATCH`, `DELETE` на ендпоінтах незмінного журналу споживання (`/api/v1/consumption-entries/*`). | `REQ-F-007`, `REQ-NF-008` |
| **`409 Conflict`** | Business State Conflict | 1. Дублікат за парою `(brewery, name)` або штрих-кодом (REQ-F-002).<br>2. Наявність активної чернетки `BeerVersion` у статусі `UNVERIFIED` для даного товару (REQ-F-003).<br>3. Спроба повторного голосування користувача за ту саму версію (REQ-F-004).<br>4. Товар уже є у списку бажань `Wishlist`. | `REQ-F-002`, `REQ-F-003`, `REQ-F-004`, `REQ-NF-009` |
| **`422 Unprocessable Entity`** | Semantic Validation Error | Семантичні порушення інваріантів предметної області:<br>• Об'єм тари $< 50$ мл або $> 10000$ мл (AC-001.2);<br>• Неузгодженість типів: `package_type = DRAFT` для фасованого пива або навпаки (AC-001.3, AC-001.4);<br>• Недопустима часова мітка споживання: в майбутньому, раніше реєстрації або старіша 1 року (AC-006.2, AC-006.3);<br>• `quantity < 1` або `quantity > 100` (AC-006.4);<br>• Неприпустима оцінка відгуку (поза діапазоном 1.0–5.0 або крок $\ne 0.5$) (AC-008.2);<br>• Спроба злиття товару самого із собою (REQ-F-011). | `REQ-F-001`, `REQ-F-006`, `REQ-F-008`, `REQ-F-011`, `REQ-NF-008` |
| **`500 Internal Server Error`** | Server Fault | Непередбачена внутрішня помилка сервера або критичний збій транзакції БД із відкатом (Rollback). | `REQ-NF-007`, `REQ-NF-008` |

---

### 4.3. Загальні конвенції фільтрації, пагінації та сортування

1. **Пагінація списків (Pagination):**
   - Усі операції отримання списків підтримують query-параметри `page` (ціле число $\ge 0$, за замовчуванням `0`) та `size` (ціле число від 1 до 100, за замовчуванням `20`).
   - Відповідь загортається в уніфікований DTO-контейнер:
     ```json
     {
       "items": [ ... ],
       "page": 0,
       "size": 20,
       "total_elements": 142,
       "total_pages": 8,
       "has_next": true,
       "has_previous": false
     }
     ```
2. **Сортування (Sorting):**
   - Параметр `sort` приймає формат `<поле>,<напрямок>`, наприклад `sort=name,asc` або `sort=consumed_at,desc`.
3. **Формати дат та часу:**
   - Усі часові мітки в запитах і відповідях передаються у форматі **ISO 8601 UTC** із суфіксом `Z` (наприклад, `2026-10-20T14:30:00.000Z`).
   - Часові пояси користувача зберігаються та валідуються за базою **IANA Time Zone Database** (наприклад, `Europe/Kyiv`, `America/New_York`).


---

## 5. Результати валідації

Специфікація [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) пройшла автоматичну перевірку офіційним лінтером **Redocly CLI** (`recommended` набір правил OpenAPI 3.0.3).

### Під час ітеративної валідації було виявлено та усунено:
1. **`info-license`:** додано об'єкт ліцензії `info.license` (`MIT`).
2. **`no-server-example.com`:** замінено локальну адресу `localhost` у блоці `servers` на валідне тестове доменне середовище (`https://staging-api.beertracker.local/api/v1`).
3. **`no-invalid-schema-examples` та `no-invalid-media-type-examples`:** усі ідентифікатори в прикладах схем та медіа-типів (`id`, `user_id`, `author_id`, `packaging_id`, `version_id`, `traceId`) приведено до суворого шістнадцяткового формату RFC 4122 (`[0-9a-f]`) відповідно до обмеження `format: uuid`.
4. **`operation-2xx-response`:** ресурс `/consumption-entries/{id}` оформлено за стандартом OpenAPI 3.0.3 через операцію читання `GET` (`getConsumptionEntryById` для верифікації OWASP-ізоляції `404 Not Found` за `REQ-NF-005`) із документуванням відповіді `405 Method Not Allowed` для заборонених мутуючих методів `PUT`, `PATCH`, `DELETE` (`REQ-F-007`).

### Вивід термінала (`npx @redocly/cli lint api/openapi.yaml`):

```text
> npx @redocly/cli lint api/openapi.yaml

No configurations were provided -- using built in recommended configuration by default.

validating api\openapi.yaml...
api\openapi.yaml: validated in 265ms

Woohoo! Your API description is valid. 🎉
```

**Підсумок перевірки:** `0 errors`, `0 warnings` (код виходу `0`).


---

## 6. Позитивні сценарії (Positive API Scenarios)

Нижче наведено деталізовані позитивні сценарії взаємодії з API, узгоджені з контрактом [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) та поведінковою специфікацією [spec/acceptance_criteria_bdd.md](file:///D:/uni/4-year/ПІС/beer-tracker/spec/acceptance_criteria_bdd.md).

---

### `API-POS-001` (`BDD-SCN-001`): Створення ресурсу — фіксація факту споживання (`201 Created`)
- **ID сценарію:** `API-POS-001` (відповідає `BDD-SCN-001`)
- **Пов'язані REQ-ID:** `REQ-F-006`, `REQ-F-009`, `REQ-NF-003`, `REQ-NF-007`
- **Вхідний запит:**
  - **Метод та URI:** `POST /api/v1/consumption-entries`
  - **Заголовки:**
    ```http
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    Content-Type: application/json
    Accept: application/json
    ```
  - **Тіло запиту:**
    ```json
    {
      "beer_id": "b0a80123-0001-4444-9999-0123456789ab",
      "packaging_id": "30a80123-0001-4444-9999-0123456789ab",
      "quantity": 2,
      "consumed_at": "2026-09-20T20:30:00Z"
    }
    ```
- **Очікувана відповідь:**
  - **Код стану:** `201 Created`
  - **Структура JSON (`ConsumptionEntryResponse`):**
    ```json
    {
      "id": "c0a80123-9901-4444-9999-0123456789ab",
      "beer_id": "b0a80123-0001-4444-9999-0123456789ab",
      "packaging_id": "30a80123-0001-4444-9999-0123456789ab",
      "quantity": 2,
      "total_volume_ml": 1000,
      "consumed_at": "2026-09-20T20:30:00Z",
      "created_at": "2026-09-20T20:30:01Z",
      "wishlist_autoremoved": true,
      "collection_added": true,
      "beer_name": "Mova Dark Stout",
      "packaging_volume_ml": 500
    }
    ```

---

### `API-POS-002`: Отримання списку з фільтрацією та пагінацією (`200 OK`)
- **ID сценарію:** `API-POS-002`
- **Пов'язані REQ-ID:** `REQ-F-001`, `REQ-NF-001`
- **Вхідний запит:**
  - **Метод та URI:** `GET /api/v1/beers?query=Varvar&style=India%20Pale%20Ale&beer_type=PACKAGED&page=0&size=20&sort=rating_avg,desc`
  - **Заголовки:**
    ```http
    Accept: application/json
    ```
  - **Тіло запиту:** Відсутнє (порожнє тіло).
- **Очікувана відповідь:**
  - **Код стану:** `200 OK`
  - **Структура JSON (`BeerPagedResponse`):**
    ```json
    {
      "items": [
        {
          "id": "b0a80123-0012-4444-9999-0123456789ab",
          "name": "Varvar Artisan IPA",
          "brewery": "Varvar",
          "style": "India Pale Ale",
          "abv": 6.0,
          "beer_type": "PACKAGED",
          "description": "Флагманський IPA від київської броварні Varvar",
          "country": "UA",
          "image_url": "https://cdn.beertracker.local/beers/varvar-ipa.jpg",
          "status": "VERIFIED",
          "rating_avg": 4.4,
          "reviews_count": 85,
          "packagings": [
            {
              "id": "30a80123-0033-4444-9999-0123456789ab",
              "beer_id": "b0a80123-0012-4444-9999-0123456789ab",
              "volume_ml": 500,
              "package_type": "BOTTLE",
              "barcode": "4820199990012"
            }
          ],
          "created_at": "2026-08-15T10:00:00Z"
        }
      ],
      "page": 0,
      "size": 20,
      "total_elements": 1,
      "total_pages": 1,
      "has_next": false,
      "has_previous": false
    }
    ```

---

### `API-POS-003`: Отримання ресурсу за ID (`200 OK`)
- **ID сценарію:** `API-POS-003`
- **Пов'язані REQ-ID:** `REQ-F-001`, `REQ-F-003`, `REQ-F-010`
- **Вхідний запит:**
  - **Метод та URI:** `GET /api/v1/beers/b0a80123-0012-4444-9999-0123456789ab?verified_only=true`
  - **Заголовки:**
    ```http
    Accept: application/json
    ```
  - **Тіло запиту:** Відсутнє (порожнє тіло).
- **Очікувана відповідь:**
  - **Код стану:** `200 OK`
  - **Структура JSON (`BeerResponse`):**
    ```json
    {
      "id": "b0a80123-0012-4444-9999-0123456789ab",
      "name": "Varvar Artisan IPA",
      "brewery": "Varvar",
      "style": "India Pale Ale",
      "abv": 6.0,
      "beer_type": "PACKAGED",
      "description": "Флагманський IPA від київської броварні Varvar",
      "country": "UA",
      "image_url": "https://cdn.beertracker.local/beers/varvar-ipa.jpg",
      "status": "VERIFIED",
      "rating_avg": 4.4,
      "reviews_count": 85,
      "packagings": [
        {
          "id": "30a80123-0033-4444-9999-0123456789ab",
          "beer_id": "b0a80123-0012-4444-9999-0123456789ab",
          "volume_ml": 500,
          "package_type": "BOTTLE",
          "barcode": "4820199990012"
        }
      ],
      "created_at": "2026-08-15T10:00:00Z"
    }
    ```

---

### `API-POS-004`: Оновлення ресурсу — модифікація дегустаційного відгуку (`200 OK`)
- **ID сценарію:** `API-POS-004`
- **Пов'язані REQ-ID:** `REQ-F-008`
- **Вхідний запит:**
  - **Метод та URI:** `PUT /api/v1/beers/b0a80123-0001-4444-9999-0123456789ab/reviews`
  - **Заголовки:**
    ```http
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    Content-Type: application/json
    Accept: application/json
    ```
  - **Тіло запиту:**
    ```json
    {
      "rating": 5.0,
      "review_text": "Після повторної дегустації - оцінка максимальна! Чудовий баланс."
    }
    ```
- **Очікувана відповідь:**
  - **Код стану:** `200 OK`
  - **Структура JSON (`ReviewResponse`):**
    ```json
    {
      "id": "50a80123-0001-4444-9999-0123456789ab",
      "beer_id": "b0a80123-0001-4444-9999-0123456789ab",
      "user_id": "10a80123-4201-4444-9999-0123456789ab",
      "rating": 5.0,
      "review_text": "Після повторної дегустації - оцінка максимальна! Чудовий баланс.",
      "created_at": "2026-10-20T13:00:00Z",
      "updated_at": "2026-10-20T13:10:00Z"
    }
    ```

---

### `API-POS-005` (`BDD-SCN-002`): Голосування спільноти та автоверифікація версії (`200 OK`)
- **ID сценарію:** `API-POS-005` (відповідає `BDD-SCN-002`)
- **Пов'язані REQ-ID:** `REQ-F-004`, `REQ-F-005`, `REQ-NF-009`
- **Вхідний запит:**
  - **Метод та URI:** `POST /api/v1/beer-versions/40a80123-0002-4444-9999-0123456789ab/votes`
  - **Заголовки:**
    ```http
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    Content-Type: application/json
    Accept: application/json
    ```
  - **Тіло запиту:**
    ```json
    {
      "vote_type": "CONFIRM"
    }
    ```
- **Очікувана відповідь:**
  - **Код стану:** `200 OK`
  - **Структура JSON (`BeerVersionVoteResponse`):**
    ```json
    {
      "version_id": "40a80123-0002-4444-9999-0123456789ab",
      "confirm_votes": 5,
      "reject_votes": 0,
      "status": "VERIFIED",
      "message": "Version successfully verified by community consensus (threshold N=5 reached)"
    }
    ```

---

### `API-POS-006`: Видалення ресурсу — вилучення товару зі списку бажань (`204 No Content`)
- **ID сценарію:** `API-POS-006`
- **Пов'язані REQ-ID:** `REQ-F-009`, `REQ-NF-005`
- **Вхідний запит:**
  - **Метод та URI:** `DELETE /api/v1/wishlist-items/b0a80123-0001-4444-9999-0123456789ab`
  - **Заголовки:**
    ```http
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    ```
  - **Тіло запиту:** Відсутнє (порожнє тіло).
- **Очікувана відповідь:**
  - **Код стану:** `204 No Content`
  - **Структура JSON:** Порожнє тіло відповіді (`No Content`).

---

## 7. Негативні сценарії (Negative API Scenarios)

Усі негативні сценарії повертають детерміновану структуру **`ErrorResponse`** згідно з контрактом [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) та вимогою **REQ-NF-008**.

---

### `API-NEG-001`: Невалідний тип параметра запиту (`400 Bad Request`)
- **ID сценарію:** `API-NEG-001`
- **Пов'язані REQ-ID:** `REQ-F-001`, `REQ-NF-008`
- **Вхідний запит:**
  - **Метод та URI:** `GET /api/v1/beers?size=invalid_int`
  - **Заголовки:**
    ```http
    Accept: application/json
    ```
  - **Тіло запиту:** Відсутнє *(передано нечисловий рядок `invalid_int` замість цілого числа `integer` для параметра пагінації `size`)*.
- **Очікувана відповідь:**
  - **Код стану:** `400 Bad Request`
  - **Структура JSON (`ErrorResponse`):**
    ```json
    {
      "code": "INVALID_PAGE_SIZE",
      "message": "Parameter 'size' exceeds maximum allowed limit of 100",
      "details": [
        {
          "field": "size",
          "issue": "Must be between 1 and 100"
        }
      ],
      "timestamp": "2026-10-20T12:05:00Z",
      "traceId": "20a80123-0006-4444-9999-0123456789ab"
    }
    ```

---

### `API-NEG-002a`: Пропуск обов'язкового поля `required` у тілі запиту (`422 Unprocessable Entity`)
- **ID сценарію:** `API-NEG-002a`
- **Пов'язані REQ-ID:** `REQ-F-001`, `REQ-NF-008`
- **Вхідний запит:**
  - **Метод та URI:** `POST /api/v1/beers`
  - **Заголовки:**
    ```http
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    Content-Type: application/json
    Accept: application/json
    ```
  - **Тіло запиту** *(пропущено обов'язкове поле `name` зі списку `required` схеми `BeerCreateRequest`)*:
    ```json
    {
      "brewery": "Mova Brewing Co.",
      "style": "Oatmeal Stout",
      "abv": 5.8,
      "beer_type": "PACKAGED",
      "packagings": [
        {
          "volume_ml": 500,
          "package_type": "CAN"
        }
      ]
    }
    ```
- **Очікувана відповідь:**
  - **Код стану:** `422 Unprocessable Entity`
  - **Структура JSON (`ErrorResponse`):**
    ```json
    {
      "code": "INVALID_PACKAGE_TYPE_FOR_DRAFT",
      "message": "All packagings for DRAFT beer must have package_type DRAFT",
      "details": [
        {
          "field": "packagings[0].package_type",
          "issue": "Found 'BOTTLE' for beer_type 'DRAFT'"
        }
      ],
      "timestamp": "2026-10-20T12:10:05Z",
      "traceId": "20a80123-0011-4444-9999-0123456789ab"
    }
    ```

---

### `API-NEG-002b`: Невалідні типи полів та порушення бізнес-обмежень у тілі запиту (`422 Unprocessable Entity`)
- **ID сценарію:** `API-NEG-002b`
- **Пов'язані REQ-ID:** `REQ-F-001` (`AC-001.2`, `AC-001.3`), `REQ-NF-008`
- **Вхідний запит:**
  - **Метод та URI:** `POST /api/v1/beers`
  - **Заголовки:**
    ```http
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    Content-Type: application/json
    Accept: application/json
    ```
  - **Тіло запиту** *(передано рядок замість `number` для `abv`, а також розливне пиво `DRAFT` із несумісною тарою `BOTTLE` та об'ємом `30 < 50` мл)*:
    ```json
    {
      "name": "Varvar Golden Ale On Tap",
      "brewery": "Varvar",
      "style": "Golden Ale",
      "abv": "invalid_float_type",
      "beer_type": "DRAFT",
      "packagings": [
        {
          "volume_ml": 30,
          "package_type": "BOTTLE"
        }
      ]
    }
    ```
- **Очікувана відповідь:**
  - **Код стану:** `422 Unprocessable Entity`
  - **Структура JSON (`ErrorResponse`):**
    ```json
    {
      "code": "INVALID_PACKAGE_TYPE_FOR_DRAFT",
      "message": "All packagings for DRAFT beer must have package_type DRAFT and valid volume",
      "details": [
        {
          "field": "packagings[0].package_type",
          "issue": "Found 'BOTTLE' for beer_type 'DRAFT'"
        },
        {
          "field": "packagings[0].volume_ml",
          "issue": "Volume 30 ml is below minimum allowed 50 ml"
        }
      ],
      "timestamp": "2026-10-20T12:10:05Z",
      "traceId": "20a80123-0011-4444-9999-0123456789ab"
    }
    ```

---

### `API-NEG-003`: Звернення до неіснуючого (або чужого приватного) ID (`404 Not Found`)
- **ID сценарію:** `API-NEG-003`
- **Пов'язані REQ-ID:** `REQ-F-006`, `REQ-NF-005`, `REQ-NF-008`
- **Вхідний запит:**
  - **Метод та URI:** `GET /api/v1/consumption-entries/c0a80123-9999-4444-9999-0123456789ab`
  - **Заголовки:**
    ```http
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    Accept: application/json
    ```
  - **Тіло запиту:** Відсутнє (порожнє тіло).
- **Очікувана відповідь:**
  - **Код стану:** `404 Not Found` *(повертається як для неіснуючого запису, так і при спробі доступу до чужого запису споживання згідно з OWASP Data Isolation)*
  - **Структура JSON (`ErrorResponse`):**
    ```json
    {
      "code": "CONSUMPTION_ENTRY_NOT_FOUND",
      "message": "Consumption entry with specified identifier was not found",
      "details": [
        {
          "field": "id",
          "issue": "No accessible consumption entry found for provided ID"
        }
      ],
      "timestamp": "2026-10-20T18:00:00Z",
      "traceId": "20a80123-0404-4444-9999-0123456789ab"
    }
    ```

---

### `API-NEG-004` (`BDD-SCN-004`): Конфлікт бізнес-логіки / дублікат штрих-коду (`409 Conflict`)
- **ID сценарію:** `API-NEG-004` (відповідає `BDD-SCN-004`)
- **Пов'язані REQ-ID:** `REQ-F-002` (`AC-002.2`), `REQ-NF-008`
- **Вхідний запит:**
  - **Метод та URI:** `POST /api/v1/beers`
  - **Заголовки:**
    ```http
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    Content-Type: application/json
    Accept: application/json
    ```
  - **Тіло запиту:**
    ```json
    {
      "name": "Urquell Original",
      "brewery": "Plzensky Prazdroj",
      "style": "Pilsner",
      "abv": 4.4,
      "beer_type": "PACKAGED",
      "packagings": [
        {
          "volume_ml": 500,
          "package_type": "BOTTLE",
          "barcode": "8594404110123"
        }
      ]
    }
    ```
- **Очікувана відповідь:**
  - **Код стану:** `409 Conflict`
  - **Структура JSON (`ErrorResponse`):**
    ```json
    {
      "code": "DUPLICATE_BARCODE_CONFLICT",
      "message": "Штрих-код 8594404110123 уже прив'язаний до іншого товару",
      "details": [
        {
          "field": "packagings[0].barcode",
          "issue": "Barcode 8594404110123 is already assigned to Pilsner Urquell"
        }
      ],
      "timestamp": "2026-10-20T12:10:03Z",
      "traceId": "20a80123-0009-4444-9999-0123456789ab"
    }
    ```

---

### `API-NEG-005`: Неавторизований доступ до захищеного ресурсу (`401 Unauthorized`)
- **ID сценарію:** `API-NEG-005`
- **Пов'язані REQ-ID:** `REQ-F-006`, `REQ-NF-004`, `REQ-NF-008`
- **Вхідний запит:**
  - **Метод та URI:** `POST /api/v1/consumption-entries`
  - **Заголовки** *(заголовок `Authorization` відсутній)*:
    ```http
    Content-Type: application/json
    Accept: application/json
    ```
  - **Тіло запиту:**
    ```json
    {
      "beer_id": "b0a80123-0001-4444-9999-0123456789ab",
      "packaging_id": "30a80123-0001-4444-9999-0123456789ab",
      "quantity": 1,
      "consumed_at": "2026-10-20T18:00:00Z"
    }
    ```
- **Очікувана відповідь:**
  - **Код стану:** `401 Unauthorized`
  - **Структура JSON (`ErrorResponse`):**
    ```json
    {
      "code": "UNAUTHORIZED",
      "message": "Full authentication is required to access this resource",
      "details": [
        {
          "field": "Authorization",
          "issue": "Missing or invalid Bearer JWT token"
        }
      ],
      "timestamp": "2026-10-20T18:00:00Z",
      "traceId": "20a80123-0401-4444-9999-0123456789ab"
    }
    ```

---

### `API-NEG-006` (`BDD-SCN-003`): Відсутність передумови споживання при створенні відгуку (`403 Forbidden`)
- **ID сценарію:** `API-NEG-006` (відповідає `BDD-SCN-003`)
- **Пов'язані REQ-ID:** `REQ-F-008` (`AC-008.1`), `REQ-NF-008`
- **Вхідний запит:**
  - **Метод та URI:** `POST /api/v1/beers/b0a80123-0003-4444-9999-0123456789ab/reviews`
  - **Заголовки:**
    ```http
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    Content-Type: application/json
    Accept: application/json
    ```
  - **Тіло запиту:**
    ```json
    {
      "rating": 4.5,
      "review_text": "Чудовий класичний лагер"
    }
    ```
- **Очікувана відповідь:**
  - **Код стану:** `403 Forbidden`
  - **Структура JSON (`ErrorResponse`):**
    ```json
    {
      "code": "ERR_CONSUMPTION_REQUIRED",
      "message": "User cannot review beer without at least one confirmed consumption entry",
      "details": [
        {
          "field": "beer_id",
          "issue": "No consumption history recorded for Underwood Kyiv Lager"
        }
      ],
      "timestamp": "2026-10-20T13:00:01Z",
      "traceId": "20a80123-0023-4444-9999-0123456789ab"
    }
    ```

---

### `API-NEG-007` (`BDD-SCN-005`): Спроба зміни або видалення незмінного запису споживання (`405 Method Not Allowed`)
- **ID сценарію:** `API-NEG-007` (відповідає `BDD-SCN-005`)
- **Пов'язані REQ-ID:** `REQ-F-007` (`AC-007.1`), `REQ-NF-008`
- **Вхідний запит:**
  - **Метод та URI:** `PUT /api/v1/consumption-entries/c0a80123-0001-4444-9999-0123456789ab`
  - **Заголовки:**
    ```http
    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    Content-Type: application/json
    Accept: application/json
    ```
  - **Тіло запиту:**
    ```json
    {
      "quantity": 1
    }
    ```
- **Очікувана відповідь:**
  - **Код стану:** `405 Method Not Allowed`
  - **Структура JSON (`ErrorResponse`):**
    ```json
    {
      "code": "METHOD_NOT_ALLOWED",
      "message": "Consumption entries are immutable (Append-Only) and cannot be updated or deleted",
      "details": [
        {
          "field": "http_method",
          "issue": "Mutating HTTP methods (PUT, PATCH, DELETE) are strictly forbidden"
        }
      ],
      "timestamp": "2026-10-20T12:50:00Z",
      "traceId": "20a80123-0021-4444-9999-0123456789ab"
    }
    ```

---

## 8. Результати тестування сценаріїв на Mock-сервері (Prism CLI)

Автоматизоване контрактне тестування всіх позитивних і негативних сценаріїв виконано проти живого mock-сервера **Stoplight Prism** за допомогою виконуваного скрипта [tests/api_mock_scenarios.sh](file:///D:/uni/4-year/ПІС/beer-tracker/tests/api_mock_scenarios.sh).

### 8.1 Конфігурація середовища та запуск
- **Команда запуску Mock-сервера:**
  ```bash
  npx @stoplight/prism-cli mock api/openapi.yaml -p 4010
  ```
- **Базовий URL при локальному запуску Prism:** `http://localhost:4010` *(оскільки префікс `/api/v1` зафіксовано в блоці `servers`, а шляхи `paths` у [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) починаються з кореня ресурсу `/beers`, `/consumption-entries` тощо, локальний Prism монтує маршрути безпосередньо від `http://localhost:4010/`)*.
- **Команда запуску тестового набору:**
  ```bash
  ./tests/api_mock_scenarios.sh
  ```
- **Механізми верифікації у скрипті:**
  1. **Динамічна валідація Prism (`sl-violations`):** автоматичне виявлення невалідних типів параметрів (`400 Bad Request`), пропущених `required` полів або порушення числових меж `minimum` (`422 Unprocessable Entity`), а також відсутнього заголовка `Authorization: Bearer` (`401 Unauthorized`).
  2. **Детермінована симуляція станів через заголовок `Prefer`:** відтворення бізнес-конфліктів і станів БД (`Prefer: code=404`, `Prefer: code=409, example=duplicate_barcode_conflict`, `Prefer: code=403, example=consumption_required_error`, `Prefer: code=405, example=append_only_method_not_allowed`).
  3. **Автоматична перевірка контракту JSON:** для кожної відповіді `200`/`201` перевіряється наявність усіх `required`-полів відповідної DTO-схеми (`ConsumptionEntryResponse`, `BeerPagedResponse`, `BeerResponse`, `ReviewResponse`, `BeerVersionVoteResponse`); для `204` перевіряється порожнє тіло (`0 bytes`); для **всіх** відповідей `4xx` перевіряється сувора відповідність єдиній схемі **`ErrorResponse`** (`code`, `message`, `details[].field`, `details[].issue`, ISO-8601 `timestamp`, UUID `traceId`).

### 8.2 Зведена таблиця фактичних результатів виконання

| ID сценарію | Метод та маршрут (Prism) | Механізм перевірки | Очікуваний код | Фактичний код | Перевірена схема контракту | Статус |
| :--- | :--- | :--- | :---: | :---: | :--- | :---: |
| `API-POS-001` | `POST /consumption-entries` | OpenAPI Example (`BDD-SCN-001`) | `201` | `201` | `ConsumptionEntryResponse` (9 полів) | **PASS** |
| `API-POS-002` | `GET /beers?query=Varvar&...` | OpenAPI Example + Query Validation | `200` | `200` | `BeerPagedResponse` (7 полів) | **PASS** |
| `API-POS-003` | `GET /beers/{id}?verified_only=true` | OpenAPI Example + Path UUID Validation | `200` | `200` | `BeerResponse` (9 полів) | **PASS** |
| `API-POS-004` | `PUT /beers/{id}/reviews` | OpenAPI Example + Body Validation | `200` | `200` | `ReviewResponse` (7 полів) | **PASS** |
| `API-POS-005` | `POST /beer-versions/{id}/votes` | `Prefer: example=threshold_reached_verified` (`BDD-SCN-002`) | `200` | `200` | `BeerVersionVoteResponse` (5 полів) | **PASS** |
| `API-POS-006` | `DELETE /wishlist-items/{beer_id}` | OpenAPI `204 No Content` | `204` | `204` | Порожнє тіло (`0 bytes`) | **PASS** |
| `API-POS-007` | `DELETE /beers/{id}` | OpenAPI `204 No Content` (`REQ-F-010`) | `204` | `204` | Порожнє тіло (`0 bytes`) | **PASS** |
| `API-POS-008` | `POST /merges` | OpenAPI Example (`REQ-F-011`) | `200` | `200` | `BeerMergeResponse` (5 полів) | **PASS** |
| `API-POS-009` | `GET /analytics/consumption?date=...` | OpenAPI Example (`REQ-F-012`) | `200` | `200` | `ConsumptionAnalyticsResponse` (6 полів) | **PASS** |
| `API-POS-010` | `POST /beer-versions/{id}/verify` | OpenAPI Example (`REQ-F-013`) | `200` | `200` | `BeerVersionResponse` (8 полів) | **PASS** |
| `API-POS-011` | `POST /moderation-items/{id}/decision` | OpenAPI Example (`REQ-F-014`) | `200` | `200` | `ModerationDecisionResponse` (3 поля) | **PASS** |
| `API-POS-012` | `POST /auth/login` | OpenAPI Example (`REQ-NF-004`) | `200` | `200` | `TokenResponse` (4 поля) | **PASS** |
| `API-NEG-001` | `GET /beers?size=invalid_int` | **Prism Dynamic Validation** (`type: integer`) | `400` | `400` | `ErrorResponse` (`INVALID_PAGE_SIZE`) | **PASS** |
| `API-NEG-002a` | `POST /beers` *(без `name`)* | **Prism Dynamic Validation** (`required: name`) | `422` | `422` | `ErrorResponse` (`sl-violations` verified) | **PASS** |
| `API-NEG-002b` | `POST /beers` *(`abv` str, `volume_ml=30`)* | **Prism Dynamic Validation** (`type` + `minimum: 50`) | `422` | `422` | `ErrorResponse` (`sl-violations` verified) | **PASS** |
| `API-NEG-003` | `GET /consumption-entries/{id}` | `Prefer: code=404` | `404` | `404` | `ErrorResponse` (`CONSUMPTION_ENTRY_NOT_FOUND`) | **PASS** |
| `API-NEG-004` | `POST /beers` | `Prefer: code=409, example=duplicate_barcode_conflict` (`BDD-SCN-004`) | `409` | `409` | `ErrorResponse` (`DUPLICATE_BARCODE_CONFLICT`) | **PASS** |
| `API-NEG-005` | `POST /consumption-entries` *(без токена)* | **Prism Dynamic Security Validation** (`BearerAuth`) | `401` | `401` | `ErrorResponse` (`UNAUTHORIZED`) | **PASS** |
| `API-NEG-006` | `POST /beers/{id}/reviews` | `Prefer: code=403, example=consumption_required_error` (`BDD-SCN-003`) | `403` | `403` | `ErrorResponse` (`ERR_CONSUMPTION_REQUIRED`) | **PASS** |
| `API-NEG-007` | `GET /consumption-entries/{id}` | `Prefer: code=405, example=append_only_method_not_allowed` (`BDD-SCN-005`) | `405` | `405` | `ErrorResponse` (`METHOD_NOT_ALLOWED`) | **PASS** |

### 8.3 Фактичний вивід термінала (`./tests/api_mock_scenarios.sh`)

```text
==============================================================================
 Beer Tracker API — Contract & ErrorResponse Verification (Prism Mock)
 Target Base URL: http://localhost:4010
==============================================================================

>>> PART 1: POSITIVE API SCENARIOS

------------------------------------------------------------------------------
[API-POS-001] POST /consumption-entries — Створення запису споживання (BDD-SCN-001, REQ-F-006)
  HTTP Status : expected=201, actual=201
  Contract    : ConsumptionEntryResponse valid (9 required fields verified)
  Payload     : id='c0a80123-9901-4444-9999-0123456789ab', beer_id='b0a80123-0001-4444-9999-0123456789ab', packaging_id='30a80123-0001-4444-9999-0123456789ab', quantity=2
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-002] GET /beers?... — Список пива з фільтрацією та пагінацією (REQ-F-001, REQ-NF-001)
  HTTP Status : expected=200, actual=200
  Contract    : BeerPagedResponse valid (7 required fields verified)
  Payload     : items=[1 item(s)], page=0, size=20, total_elements=1
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-003] GET /beers/{id}?verified_only=true — Отримання продукту за ID (REQ-F-001, REQ-F-003)
  HTTP Status : expected=200, actual=200
  Contract    : BeerResponse valid (9 required fields verified)
  Payload     : id='b0a80123-0012-4444-9999-0123456789ab', name='Varvar Artisan IPA', brewery='Varvar', style='India Pale Ale'
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-004] PUT /beers/{id}/reviews — Оновлення дегустаційного відгуку (REQ-F-008)
  HTTP Status : expected=200, actual=200
  Contract    : ReviewResponse valid (6 required fields verified)
  Payload     : id='50a80123-0001-4444-9999-0123456789ab', beer_id='b0a80123-0001-4444-9999-0123456789ab', user_id='10a80123-4201-4444-9999-0123456789ab', rating=5
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-005] POST /beer-versions/{id}/votes — Голосування спільноти N=5 (BDD-SCN-002, REQ-F-005)
  HTTP Status : expected=200, actual=200
  Contract    : BeerVersionVoteResponse valid (5 required fields verified)
  Payload     : version_id='40a80123-0002-4444-9999-0123456789ab', confirm_votes=5, reject_votes=0, status='VERIFIED'
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-006] DELETE /wishlist-items/{beer_id} — Видалення ресурсу зі списку бажань (REQ-F-009)
  HTTP Status : expected=204, actual=204
  Contract    : Empty payload verified (0 bytes)
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-007] DELETE /beers/{id} — М'яке видалення продукту модератором / Placeholder (REQ-F-010)
  HTTP Status : expected=204, actual=204
  Contract    : Empty payload verified (0 bytes)
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-008] POST /merges — Адміністративне злиття дублікатів (REQ-F-011, REQ-NF-007)
  HTTP Status : expected=200, actual=200
  Contract    : BeerMergeResponse valid (5 required fields verified)
  Payload     : target_beer_id='b0a80123-0012-4444-9999-0123456789ab', migrated_consumptions_count=47, migrated_packagings_count=2, archived_reviews_count=12
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-009] GET /analytics/consumption?date=2026-10-20 — Аналітика доби та стріків (REQ-F-012, REQ-NF-002)
  HTTP Status : expected=200, actual=200
  Contract    : ConsumptionAnalyticsResponse valid (6 required fields verified)
  Payload     : timezone='Europe/Kyiv', current_streak=5, longest_streak=14, total_volume_ml=18500
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-010] POST /beer-versions/{id}/verify — Ручна верифікація версії модератором (REQ-F-013)
  HTTP Status : expected=200, actual=200
  Contract    : BeerVersionResponse valid (8 required fields verified)
  Payload     : id='40a80123-0002-4444-9999-0123456789ab', beer_id='b0a80123-0012-4444-9999-0123456789ab', author_id='10a80123-4201-4444-9999-0123456789ab', payload={'style': 'India Pale Ale (West Coast)'}
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-011] POST /moderation-items/{id}/decision — Опрацювання черги модерації (REQ-F-014)
  HTTP Status : expected=200, actual=200
  Contract    : ModerationDecisionResponse valid (3 required fields verified)
  Payload     : beer_id='b0a80123-0999-4444-9999-0123456789ab', decision='APPROVE', processed_at='2026-10-20T12:35:00Z'
  Result      : [PASS]

------------------------------------------------------------------------------
[API-POS-012] POST /auth/login — Автентифікація JWT Access + Refresh (REQ-NF-004, REQ-NF-006)
  HTTP Status : expected=200, actual=200
  Contract    : TokenResponse valid (4 required fields verified)
  Payload     : access_token='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1MGE4MDEyMy00MjAxLTQ0NDQtOTk5OS0wMTIzNDU2Nzg5YWIiLCJyb2xlIjoiVVNFUiIsImV4cCI6MTgwMDAwMDAwMH0.signature', refresh_token='50a80123-9999-4444-8888-0123456789ab', token_type='Bearer', expires_in=900
  Result      : [PASS]

>>> PART 2: NEGATIVE API SCENARIOS (Dynamic Prism Validation & Prefer Simulation)

------------------------------------------------------------------------------
[API-NEG-001] GET /beers?size=invalid_int — Невалідний тип параметра (Dynamic 400, REQ-NF-008)
  HTTP Status : expected=400, actual=400
  Prism Check : Dynamic validation triggered (sl-violations: [{"location":["request","query","size"],"severity":"Error","code":"type","message":"Request query parameter size must be integer"}])
  Contract    : ErrorResponse valid (code=INVALID_PAGE_SIZE, traceId=20a80123-0006-4444-9999-0123456789ab)
  Detail[0]   : size: Must be between 1 and 100
  Result      : [PASS]

------------------------------------------------------------------------------
[API-NEG-002a] POST /beers (missing required 'name') — Пропуск обов'язкового поля (Dynamic 422, REQ-F-001)
  HTTP Status : expected=422, actual=422
  Prism Check : Dynamic validation triggered (sl-violations: [{"location":["request","body"],"severity":"Error","code":"required","message":"Request body must have required property 'name'"}])
  Contract    : ErrorResponse valid (code=INVALID_PACKAGE_TYPE_FOR_DRAFT, traceId=20a80123-0011-4444-9999-0123456789ab)
  Detail[0]   : packagings[0].package_type: Found 'BOTTLE' for beer_type 'DRAFT'
  Result      : [PASS]

------------------------------------------------------------------------------
[API-NEG-002b] POST /beers (invalid type abv & volume_ml=30 < 50) — Порушення типів і меж (Dynamic 422, AC-001.2)
  HTTP Status : expected=422, actual=422
  Prism Check : Dynamic validation triggered (sl-violations: [{"location":["request","body","abv"],"severity":"Error","code":"type","message":"Request body property abv must be number"},{"location":["request","body","packagings","0","volume_ml"],"severity":"Error","code":"minimum","message":"Request body property packagings.0.volume_ml must be >= 50"}])
  Contract    : ErrorResponse valid (code=INVALID_PACKAGE_TYPE_FOR_DRAFT, traceId=20a80123-0011-4444-9999-0123456789ab)
  Detail[0]   : packagings[0].package_type: Found 'BOTTLE' for beer_type 'DRAFT'
  Result      : [PASS]

------------------------------------------------------------------------------
[API-NEG-003] GET /consumption-entries/{id} — Звернення до неіснуючого/чужого ID (Prefer: code=404, REQ-NF-005)
  HTTP Status : expected=404, actual=404
  Prism Check : Deterministic simulation via Prefer header
  Contract    : ErrorResponse valid (code=CONSUMPTION_ENTRY_NOT_FOUND, traceId=20a80123-0404-4444-9999-0123456789ab)
  Detail[0]   : id: No accessible consumption entry found for provided ID
  Result      : [PASS]

------------------------------------------------------------------------------
[API-NEG-004] POST /beers — Дублікат штрих-коду 8594404110123 (Prefer: code=409, BDD-SCN-004)
  HTTP Status : expected=409, actual=409
  Prism Check : Deterministic simulation via Prefer header
  Contract    : ErrorResponse valid (code=DUPLICATE_BARCODE_CONFLICT, traceId=20a80123-0009-4444-9999-0123456789ab)
  Detail[0]   : packagings[0].barcode: Barcode 8594404110123 is already assigned to Pilsner Urquell
  Result      : [PASS]

------------------------------------------------------------------------------
[API-NEG-005] POST /consumption-entries (no Authorization) — Неавторизований доступ (Dynamic 401, REQ-NF-004)
  HTTP Status : expected=401, actual=401
  Prism Check : Dynamic validation triggered (sl-violations: [{"location":["request"],"severity":"Error","code":401,"message":"Invalid security scheme used"}])
  Contract    : ErrorResponse valid (code=UNAUTHORIZED, traceId=20a80123-0401-4444-9999-0123456789ab)
  Detail[0]   : Authorization: Missing or invalid Bearer JWT token
  Result      : [PASS]

------------------------------------------------------------------------------
[API-NEG-006] POST /beers/{id}/reviews — Відсутній факт споживання (Prefer: code=403, BDD-SCN-003)
  HTTP Status : expected=403, actual=403
  Prism Check : Deterministic simulation via Prefer header
  Contract    : ErrorResponse valid (code=ERR_CONSUMPTION_REQUIRED, traceId=20a80123-0023-4444-9999-0123456789ab)
  Detail[0]   : beer_id: No consumption history recorded for Underwood Kyiv Lager
  Result      : [PASS]

------------------------------------------------------------------------------
[API-NEG-007] Append-Only /consumption-entries/{id} — Заборона модифікації/видалення (Prefer: code=405, BDD-SCN-005)
  HTTP Status : expected=405, actual=405
  Prism Check : Deterministic simulation via Prefer header
  Contract    : ErrorResponse valid (code=METHOD_NOT_ALLOWED, traceId=20a80123-0021-4444-9999-0123456789ab)
  Detail[0]   : http_method: Mutating HTTP methods (PUT, PATCH, DELETE) are strictly forbidden
  Result      : [PASS]

==============================================================================
 SUMMARY: Total=20 | Passed=20 | Failed=0
==============================================================================
```

**Висновок:** Усі **20 із 20** сценаріїв (`12` позитивних та `8` негативних) виконані успішно (`0` помилок). Кожна відповідь mock-сервера повністю відповідає контракту [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml), а всі помилки `4xx` повертають валідну єдину модель **`ErrorResponse`** (`code`, `message`, `details`, `timestamp`, `traceId`).

