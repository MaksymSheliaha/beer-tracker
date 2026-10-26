# Офіційний адендум до специфікації вимог (SRS Addendum)
## Уточнення та розширення контрактів за результатами API-First проєктування (`OpenAPI 3.0.3`)

**Ідентифікатор документа:** `SRS-ADD-001`  
**Базова специфікація:** [spec/SRS.md](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md) (версія `2.1.0`, IEEE Std 830-1998)  
**Нормативний глосарій:** [spec/domain_glossary.md](file:///D:/uni/4-year/ПІС/beer-tracker/spec/domain_glossary.md) (версія `2.0.0`)  
**Формальний API-контракт:** [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) (`OpenAPI 3.0.3`, версія `2.1.0`)  
**Супутній дизайн-документ:** [docs/api_design.md](file:///D:/uni/4-year/ПІС/beer-tracker/docs/api_design.md)  
**Дата затвердження:** `2026-10-26`  
**Статус:** `APPROVED (Contract Baseline)`

---

## 1. Призначення документа

Цей адендум фіксує всі архітектурні, структурні та поведінкові уточнення, вироблені під час формалізації REST API у специфікації [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml), які були відсутні або описані лише концептуально у початковій редакції [spec/SRS.md](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md).

Положення цього документа мають статус **обов'язкового доповнення до Розділу 3 (`Specific Requirements`) SRS** і є нормативною базою для етапів імплементації бекенду та автоматизованого приймального тестування (TDD / BDD).

---

## 2. Нормалізація REST-ресурсів, URI-ієрархії та HTTP-методів (доповнення до п. 3.2.1 SRS)

У початковій таблиці п. `3.2.1` [spec/SRS.md](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md#L254-L285) частина маршрутів використовувала однину (`/consumption`, `/wishlist`, `/collection`), дієслівні сегменти шляху (`/beers/versions/pending`, `/vote`) або об'єднувала створення й модифікацію в один метод. У [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) маршрути приведено у сувору відповідність до канонів REST (іменники у множині `kebab-case`) та сутностей [spec/domain_glossary.md](file:///D:/uni/4-year/ПІС/beer-tracker/spec/domain_glossary.md).

### 2.1. Таблиця трансформації та доданих ендпоінтів

| Початковий опис у SRS (§3.2.1) | Канонічний контракт у `api/openapi.yaml` | `operationId` | Зміст уточнення / обґрунтування |
| :--- | :--- | :--- | :--- |
| `GET /api/v1/beers/versions/pending` | `GET /api/v1/beer-versions?status=UNVERIFIED` | `listPendingBeerVersions` | Заміна спеціального підшляху `/pending` на стандартну query-фільтрацію колекції ресурсів `beer-versions`. |
| `POST /api/v1/beers/versions/{ver_id}/vote` | `POST /api/v1/beer-versions/{id}/votes` | `voteBeerVersion` | Винесення `beer-versions` у кореневий ресурс та іменування вкладеної колекції голосів у множині (`votes`). |
| `POST /api/v1/moderation/versions/{ver_id}/verify` | `POST /api/v1/beer-versions/{id}/verify` | `verifyBeerVersion` | Консолідація життєвого циклу версії навколо ресурсу `beer-versions/{id}`. |
| `POST /api/v1/moderation/versions/{ver_id}/reject` | `POST /api/v1/beer-versions/{id}/reject` | `rejectBeerVersion` | Консолідація відхилення чернетки в межах ресурсу `beer-versions/{id}`. |
| `GET /api/v1/moderation/queue` | `GET /api/v1/moderation-items/queue` | `listModerationQueue` | Узгодження назви колекції елементів черги модерації у множині (`moderation-items`). |
| `POST /api/v1/moderation/beers/{id}/decision` | `POST /api/v1/moderation-items/{id}/decision` | `resolveModerationQueueItem` | Уніфікація URI опрацювання елемента черги модерації. |
| `POST /api/v1/beers/merge` | `POST /api/v1/merges` | `mergeBeers` | Моделювання адміністративної транзакції `Merge` (`REQ-F-011`) як створення ресурсу операції злиття. |
| `GET /api/v1/consumption`<br>`POST /api/v1/consumption` | `GET /api/v1/consumption-entries`<br>`POST /api/v1/consumption-entries` | `listConsumptionEntries`<br>`recordConsumptionEntry` | Перехід від однини `consumption` до множини `consumption-entries` відповідно до терміна `ConsumptionEntry` у глосарії. |
| *(Відсутній у таблиці §3.2.1 SRS)* | **`GET /api/v1/consumption-entries/{id}`** | `getConsumptionEntryById` | **Новий ендпоінт:** читання окремого запису споживання за UUID, необхідний для перевірки ізоляції чужих даних (`REQ-NF-005` $\rightarrow$ `404`) та блокування мутацій на рівні ресурсу (`REQ-F-007` $\rightarrow$ `405`). |
| `GET /api/v1/consumption/analytics` | `GET /api/v1/analytics/consumption` | `getConsumptionAnalytics` | Групування аналітичних проекцій у просторі `/analytics/*`. |
| *(Відсутній окремий маршрут у SRS)* | **`GET /api/v1/streaks`** | `getStreaks` | **Новий ендпоінт:** виділений легковаговий ресурс для швидкого читання `Current Streak` та `Longest Streak` (`REQ-F-012`, `REQ-NF-002`) без розрахунку 24-годинної гістограми. |
| `GET /api/v1/wishlist`<br>`POST /api/v1/wishlist/{beer_id}`<br>`DELETE /api/v1/wishlist/{beer_id}` | `GET /api/v1/wishlist-items`<br>`POST /api/v1/wishlist-items/{beer_id}`<br>`DELETE /api/v1/wishlist-items/{beer_id}` | `listWishlistItems`<br>`addWishlistItem`<br>`removeWishlistItem` | Приведення назви ресурсу до сутності `WishlistItem` у множині (`wishlist-items`). |
| `GET /api/v1/collection` | `GET /api/v1/collections/tasted` | `getTastedCollection` | Явна ідентифікація віртуальної проекції скуштованих сортів (`Tasted Collection`). |
| Початково: `POST /api/v1/reviews/{beer_id}` *(створення або зміна)* | `POST /api/v1/beers/{id}/reviews`<br>`PUT /api/v1/beers/{id}/reviews`<br>`DELETE /api/v1/beers/{id}/reviews` | `createReview`<br>`updateReview`<br>`deleteReview` | Вкладення відгуків під ресурс `/beers/{id}/reviews` та чітке розмежування створення (`POST` $\rightarrow$ `201`) й ідемпотентного оновлення (`PUT` $\rightarrow$ `200`) згідно з `AC-008.3`. |

---

## 3. Єдиний формат помилок `ErrorResponse` (уточнення `REQ-NF-008` та зв'язок із `REQ-NF-010`)

### 3.1. Гармонізація схеми помилки
У початковій редакції `REQ-NF-008` ([spec/SRS.md](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md#L408-L409)) було зазначено загальний перелік полів RFC 7807 (`type`, `title`, `status`, `detail`, `instance`, `invalid_params`). Під час проєктування [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L2729-L2776) формат помилок стандартизовано у єдину детерміновану схему **`ErrorResponse`** (`Content-Type: application/json`), застосовану до **всіх** відповідей `4xx` та `5xx`:

| Поле в початковій SRS (`REQ-NF-008`) | Поле в `api/openapi.yaml` (`ErrorResponse`) | Тип та обмеження | Пояснення уточнення |
| :--- | :--- | :--- | :--- |
| `type` + `title` | **`code`** (`required`) | `string` (`SCREAMING_SNAKE_CASE`) | Стабільний машиночитний доменний код помилки замість URI-посилання `type`, що спрощує клієнтську обробку та BDD-перевірки (наприклад, `ERR_CONSUMPTION_REQUIRED` із `BDD-SCN-003`). |
| `detail` | **`message`** (`required`) | `string` | Узагальнене людиночитне пояснення причини відхилення запиту. |
| `invalid_params` *(структуру не було визначено)* | **`details`** (`required`) | `array` об'єктів [`ErrorDetail`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L2762-L2776) | Суворо типізований масив помилок по полях; кожен елемент містить два обов'язкові рядкові поля: **`field`** (назва поля/параметра/заголовка) та **`issue`** (конкретний опис порушення). |
| *(Відсутнє у `REQ-NF-008`)* | **`timestamp`** (`required`) | `string` (`format: date-time`, UTC) | Точна часова мітка виникнення помилки в форматі ISO 8601. |
| `instance` | **`traceId`** (`required`) | `string` (`format: uuid`) | Пряма інтеграція з вимогою `REQ-NF-010`: повертає клієнту UUID контексту трасування `MDC trace_id` для миттєвої кореляції HTTP-відповіді із записами в `logs/beer-tracker.json.log`. |

### 3.2. Реєстр стандартизованих доменних кодів помилок (`ErrorResponse.code`)
В [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) зафіксовано перелік детермінованих кодів помилок, яких не було в початковому тексті SRS (де був згаданий лише `ERR_CONSUMPTION_REQUIRED`):
- **Автентифікація та доступ (`401` / `403`):** `UNAUTHORIZED`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `FORBIDDEN`, `SELF_VOTING_FORBIDDEN`, `ERR_CONSUMPTION_REQUIRED`.
- **Валідація запитів та бізнес-інваріантів (`400` / `422`):** `MALFORMED_JSON`, `BAD_REQUEST`, `INVALID_PAGE_SIZE`, `UNPROCESSABLE_ENTITY`, `INVALID_PACKAGE_TYPE_FOR_DRAFT`, `VOLUME_OUT_OF_BOUNDS`, `CONSUMPTION_TIME_IN_FUTURE`, `INVALID_QUANTITY`, `INVALID_RATING_VALUE`, `CANNOT_MERGE_BEER_INTO_ITSELF`.
- **Пошук ресурсів та ізоляція даних (`404`):** `NOT_FOUND`, `BEER_NOT_FOUND`, `CONSUMPTION_ENTRY_NOT_FOUND`, `REVIEW_NOT_FOUND`, `WISHLIST_ITEM_NOT_FOUND`.
- **Конфлікти стану та унікальності (`409`):** `EMAIL_ALREADY_EXISTS`, `DUPLICATE_BARCODE_CONFLICT`, `DUPLICATE_BEER_NAME_CONFLICT`, `PENDING_VERSION_CONFLICT`, `DUPLICATE_VOTE_CONFLICT`, `REVIEW_ALREADY_EXISTS`, `WISHLIST_ITEM_ALREADY_EXISTS`.
- **Незмінність журналу (`405`):** `METHOD_NOT_ALLOWED`.

---

## 4. Специфікація пагінації, фільтрації та сортування (нове доповнення до SRS)

Початкова специфікація [spec/SRS.md](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md) вимагала швидкодії пошуку та фільтрації (`REQ-NF-001`), але не визначала параметри запитів і формат сторінкових відповідей. У [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) впроваджено єдиний стандарт:

### 4.1. Єдиний механізм пагінації
1. **Вхідні query-параметри:**
   - `page` — індекс сторінки з нуля (`type: integer`, `minimum: 0`, `default: 0`);
   - `size` — розмір сторінки (`type: integer`, `minimum: 1`, `maximum: 100`, `default: 20`; для `GET /wishlist-items` `default: 50`). Передача значення поза діапазоном `[1..100]` або нецілого типу відхиляється зі статусом `400 Bad Request`.
2. **Вихідні контейнери сторінкових відповідей:**
   - Схеми [`BeerPagedResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3083-L3116) та [`ConsumptionPagedResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3438-L3471) повертають обов'язкові поля: `items` (`array`), `page`, `size`, `total_elements`, `total_pages`, `has_next` (`boolean`), `has_previous` (`boolean`).
   - Схеми [`BeerVersionPagedResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3201-L3226), [`WishlistPagedResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3651-L3676) та [`CollectionPagedResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3714-L3739) повертають: `items` (`array`), `page`, `size`, `total_elements`, `total_pages`.

### 4.2. Матриця query-параметрів фільтрації та сортування

| Ендпоінт (`api/openapi.yaml`) | Додані Query-параметри | Типи, обмеження та значення за замовчуванням |
| :--- | :--- | :--- |
| `GET /beers` (`listBeers`) | `query`, `brewery`, `style`, `country`, `beer_type`, `barcode`, `page`, `size`, `sort` | `country`: `minLength: 2, maxLength: 2` (ISO 3166-1 alpha-2);<br>`beer_type`: `enum [PACKAGED, DRAFT]`;<br>`sort`: формат `field,direction`, `default: "name,asc"` (підтримує `"rating_avg,desc"`). |
| `GET /beers/{id}` (`getBeerById`) | `verified_only` | `boolean`, `default: false` (формалізує `AC-003.3`). |
| `GET /beer-versions` (`listPendingBeerVersions`) | `status`, `page`, `size` | `status`: `enum [UNVERIFIED, VERIFIED, REJECTED]`, `default: UNVERIFIED`. |
| `GET /moderation-items/queue` (`listModerationQueue`) | `page`, `size` | `page >= 0` (`default: 0`), `size 1..100` (`default: 20`). |
| `GET /consumption-entries` (`listConsumptionEntries`) | `date_from`, `date_to`, `beer_id`, `page`, `size` | `date_from`, `date_to`: ISO 8601 `format: date` (`YYYY-MM-DD`);<br>`beer_id`: `format: uuid` (фільтр історії за конкретним напоєм). |
| `GET /analytics/consumption` (`getConsumptionAnalytics`) | `date`, `date_from`, `date_to` | Усі — `format: date` (`YYYY-MM-DD`): `date` задає цільову добу для погодинного профілю (`AC-012.3`), `date_from`/`date_to` — довільний інтервал агрегації. |
| `GET /wishlist-items` (`listWishlistItems`) | `page`, `size` | `page >= 0` (`default: 0`), `size 1..100` (`default: 50`). |
| `GET /collections/tasted` (`getTastedCollection`) | `page`, `size`, `sort` | `sort`: `string`, `default: "last_consumed_at,desc"`. |

---

## 5. Розділення DTO, інформаційна безпека полів та додаткові обмеження валідації (доповнення до п. 3.2.2 SRS)

### 5.1. Відокремлення Request / Response DTO та приховування внутрішніх полів БД
У п. `3.2.2` [spec/SRS.md](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md#L286-L383) наведено реляційну модель таблиць БД (ERD). З метою запобігання витоку внутрішньої структури сховища та чутливих атрибутів (OWASP API3), у `components/schemas` специфікації [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) вхідні моделі створення/оновлення (`*CreateRequest`, `*UpdateRequest`) повністю відділені від моделей відповідей (`*Response`). Наступні службові колонки БД **ніколи не повертаються** у зовнішніх JSON-відповідях:
- `User.password_hash` та алгоритми хешування (приховано у `UserResponse`);
- `Beer.is_deleted`, `Beer.merged_into_beer_id` та внутрішній стан `Beer.status = MERGED` (приховано у `BeerResponse`; видалені/злиті записи повертають `404` у каталозі або трансформуються у `"Видалений продукт"` у журналі споживання);
- `BeerVersion.payload` типу `jsonb` (замінено нетипізований SQL-об'єкт на строго типізовану схему `BeerVersionCreateRequest`);
- `ConsumptionEntry.user_id` та `WishlistItem.user_id` (приватні ресурси завжди належать автентифікованому суб'єкту JWT);
- `Review.is_archived` та `Review.archived_at` (внутрішні системні прапорці ORM для архівації при `Merge` повністю виключені з `ReviewResponse`; клієнту повертаються лише активні відгуки з полями `created_at`/`updated_at`).

### 5.2. Нові та збагачені поля в API-схемах (порівняно з ERD у SRS)

| Схема в `api/openapi.yaml` | Додані поля | Призначення поля |
| :--- | :--- | :--- |
| [`TokenResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L2826-L2849) | `token_type` (`"Bearer"`), `expires_in` (`900`) | Явна передача типу схеми авторизації та TTL Access-токена у секундах (15 хв згідно з `REQ-NF-004`). |
| [`BeerResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3010-L3082) | `rating_avg` (`float`, `nullable`), `reviews_count` (`int`) | Агрегована середня оцінка актуальних відгуків та їхня кількість без додаткових запитів клієнта. |
| [`BeerVersionVoteResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3239-L3268) | `version_id`, `confirm_votes`, `reject_votes`, `status`, `message` | Детермінований результат голосування спільноти із повідомленням про досягнення порогу консенсусу $N=5$ (`BDD-SCN-002`). |
| [`BeerVersionRejectRequest`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3269-L3276) | `reason` (`string`, `maxLength: 1000`, optional) | Фіксація причини ручного відхилення чернетки версії модератором (`REQ-F-013`). |
| [`ModerationDecisionRequest`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3277-L3292) / [`ModerationDecisionResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3293-L3311) | `comment` (`maxLength: 1000`, optional), `processed_at` (`date-time`) | Аудит коментаря модератора та часової мітки виконання рішення щодо відхиленого товару (`REQ-F-014`). |
| [`BeerMergeResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3327-L3352) | `migrated_consumptions_count`, `migrated_packagings_count`, `archived_reviews_count`, `status` | Кількісний звіт транзакції злиття дублікатів (`REQ-F-011`), узгоджений зі схемою логування `catalog_merge_executed` (`REQ-NF-011`). |
| [`ConsumptionEntryResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3381-L3437) | `wishlist_autoremoved` (`bool`), `collection_added` (`bool`), `beer_name` (`string`), `packaging_volume_ml` (`int`) | Підтвердження атомарної синхронізації списків (`REQ-F-009`, `BDD-SCN-001`) та підтримка відображення `Placeholder Beer` (`REQ-F-010`). |
| [`StreakResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3530-L3551) | `last_consumed_date` (`date`, `nullable`), `is_active_today` (`bool`) | Додаткові індикатори стану активної серії днів споживання у таймзоні користувача. |
| [`ReviewResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3590-L3628) | `created_at` (`date-time`) | Часова мітка первинного створення відгуку (в ERD була присутня лише `updated_at`). |
| [`WishlistItemResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3629-L3650) | `beer` (`$ref: BeerResponse`) | Вбудована картка пива для відображення списку бажань за один HTTP-запит. |
| [`TastedCollectionItemResponse`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml#L3677-L3713) | `beer_id`, `beer_name`, `brewery`, `total_volume_ml`, `first_consumed_at`, `last_consumed_at`, `user_rating` | Повна формалізація віртуальної проекції `Collection` (у SRS таблиця була відсутня, оскільки це проекція над `consumption_entries`). |

### 5.3. Додаткові декларативні обмеження валідації полів
На доповнення до бізнес-інваріантів SRS (`volume_ml` `50..10000`, `quantity` `1..100`, `rating` `1.0..5.0` крок `0.5`), у [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) встановлено суворі синтаксичні й типові межі:
- **`UserRegisterRequest`:** `email` (`format: email`), `password` (`minLength: 8`, `maxLength: 128`, `format: password`), `timezone` (`string`, валідний ідентифікатор IANA).
- **`BeerCreateRequest` / `BeerVersionCreateRequest`:** `name` (`minLength: 1`, `maxLength: 255`), `brewery` (`minLength: 1`, `maxLength: 255`), `style` (`minLength: 1`, `maxLength: 100`), `abv` (`minimum: 0.0`, `maximum: 100.0`, `format: float`), `description` (`maxLength: 2000`), `country` (`minLength: 2`, `maxLength: 2`, `pattern: ^[A-Z]{2}$`), `image_url` (`format: uri`), `packagings` (`minItems: 1` при створенні продукту).
- **`BeerPackagingCreateRequest.barcode`:** `pattern: ^[0-9]{8,14}$` (дозволені лише числові коди стандартів EAN-8, UPC-A, EAN-13, GTIN-14).
- **`ReviewCreateRequest` / `ReviewUpdateRequest`:** `rating` (`minimum: 1.0`, `maximum: 5.0`, `multipleOf: 0.5`), `review_text` (`maxLength: 2000`).
- **Ідентифікатори ресурсів:** усі параметри шляху `{id}`, `{beer_id}` та зовнішні ключі у DTO мають суворе обмеження `type: string`, `format: uuid` (RFC 4122).

---

## 6. Додаткові HTTP-коди стану та заголовки відповідей

У початковому тексті [spec/SRS.md](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md) для кожної вимоги вказувалися лише профільні помилки бізнес-логіки. В [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) матрицю кодів розширено для покриття повного життєвого циклу HTTP-взаємодії:

1. **Розмежування успішних кодів `200` / `201` / `204` та заголовок `Location`:**
   - **`201 Created`** призначено для операцій створення нових сутностей (`POST /auth/register`, `POST /beers` із заголовком відповіді `Location: /api/v1/beers/{id}`, `POST /beers/{id}/versions`, `POST /consumption-entries`, `POST /beers/{id}/reviews`, `POST /wishlist-items/{beer_id}`).
   - **`200 OK`** призначено для всіх `GET`-операцій, оновлення відгуку (`PUT /beers/{id}/reviews`) та дій зміни стану (`POST /auth/login`, `/auth/refresh`, `/votes`, `/verify`, `/reject`, `/decision`, `/merges`).
   - **`204 No Content`** (без тіла відповіді) призначено для операцій видалення (`DELETE /beers/{id}`, `DELETE /beers/{id}/reviews`, `DELETE /wishlist-items/{beer_id}`).
2. **Додані коди помилок клієнта (`4xx`):**
   - **`401 Unauthorized`:** явно задокументовано на всіх захищених маршрутах (`security: [BearerAuth: []]`), а також при хибних облікових даних у `POST /auth/login` та простроченому/відкликаному токені у `POST /auth/refresh`.
   - **`409 Conflict` на додаткових ресурсах:**
     - `POST /auth/register` — конфлікт існуючого `email` (`EMAIL_ALREADY_EXISTS`);
     - `POST /beers/{id}/reviews` — спроба створити другий активний відгук замість виклику `PUT` (`REVIEW_ALREADY_EXISTS`);
     - `POST /wishlist-items/{beer_id}` — спроба повторно додати товар, який уже перебуває у вішлісті (`WISHLIST_ITEM_ALREADY_EXISTS`).
   - **`404 Not Found` на мутуючих та вкладених операціях:** додано для неіснуючих баз при створенні версії, голосуванні, модерації, злитті, записі споживання, оновленні/видаленні відгуку та видаленні з вішліста.
   - **`422 Unprocessable Entity` при адміністративному злитті та модерації:**
     - `POST /merges` — спроба злити товар сам із собою (`source_beer_id == target_beer_id`, код `CANNOT_MERGE_BEER_INTO_ITSELF`);
     - `POST /moderation-items/{id}/decision` — передача невалідного рішення поза `APPROVE | PERMANENT_DELETE`.

---

## 7. Простежуваність вимог (`x-req-id`) та детермінована контрактна верифікація

На підтримку Розділу 5 SRS (`Testing Strategy & TDD`) у специфікацію [api/openapi.yaml](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) впроваджено два додаткові механізми якості:
1. **Машиночитне трасування `x-req-id`:** кожна операція OpenAPI містить масив `x-req-id` із переліком пов'язаних функціональних (`REQ-F-001`..`REQ-F-014`) та нефункціональних (`REQ-NF-001`..`REQ-NF-009`) вимог для автоматичної перевірки повноти покриття.
2. **Іменовані приклади (`examples`) для Mock-тестування:** усі операції забезпечені іменованими прикладами успішних відповідей та помилок (`ErrorResponse`), які були успішно провалідовані лінтером Redocly CLI (`0 errors, 0 warnings`) та протестовані на mock-сервері Stoplight Prism скриптом [tests/api_mock_scenarios.sh](file:///D:/uni/4-year/ПІС/beer-tracker/tests/api_mock_scenarios.sh) (`20 / 20 PASS`).
