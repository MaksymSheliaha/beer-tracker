# Звіт про перевірку документації та автоматичний аудит матриці простежуваності

**Дата перевірки:** 2026-11-16  
**Гілка:** `dev`  
**Об'єкт перевірки:** [`README.md`](file:///D:/uni/4-year/ПІС/beer-tracker/README.md), [`docs/user-guide.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/user-guide.md), [`docs/architecture.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/architecture.md), [`docs/api_design.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/api_design.md), [`api/openapi.yaml`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml), згенерований Javadoc (`target/reports/apidocs/index.html`), [`spec/traceability-matrix.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/traceability-matrix.md).

---

## 1. Перевірка інструкцій README та інструментів збірки (Крок 8)

Перевірка виконувалась шляхом реального запуску команд з [`README.md`](file:///D:/uni/4-year/ПІС/beer-tracker/README.md) у середовищі Windows / JDK 25 / Maven 3.9.11:

| Перевірена команда | Фактичний результат | Висновок та корекції |
| :--- | :--- | :--- |
| `mvn clean verify javadoc:javadoc` | Компіляція `91` main + `10` test Java-файлів; **44/44** тести Surefire (`Failures: 0, Errors: 0, Skipped: 0`); **0** порушень Checkstyle (`checkstyle:check`); **220** HTML-сторінок Javadoc у `target/reports/apidocs/com/beertracker` без жодних помилок/попереджень (`doclint=all,-missing`); зібрано `target/beer-tracker-1.0-SNAPSHOT.jar`. | **PASS.** У [`README.md`](file:///D:/uni/4-year/ПІС/beer-tracker/README.md) виявлено й виправлено застарілий шлях Javadoc із `target/site/apidocs/index.html` на фактичний шлях плагіна `3.11.2`: `target/reports/apidocs/index.html`. |
| `npx --yes @redocly/cli lint api/openapi.yaml` | Валідація пройдена за 299 мс (`0 errors`, `1 warning` — очікуване правило `no-server-example.com` для `http://localhost:8080/api/v1`). | **PASS.** |
| Звірка копій OpenAPI-контракту | SHA-256 файлів `api/openapi.yaml` та `src/main/resources/static/openapi.yaml` побітово ідентичний (`125738184f5c9367069eafb03e307f255f1bae01d6fece67bb66c5a6cc406229`). | **PASS.** |

---

## 2. Звірка API-, архітектурної та Javadoc-документації проти фактичного коду (Крок 9)

Автоматизований аналіз вихідного коду `src/main/java/com/beertracker` порівняно з документацією показав:

1. **Ендпоінти REST API (28/28 операцій OpenAPI):**
   - Усі **28** операцій з [`api/openapi.yaml`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) наявні у 9 контролерах (`In OpenAPI but NOT in Controller: []`).
   - 3 додаткові мапінги в [`ConsumptionController`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/controller/ConsumptionController.java) (`PUT`, `PATCH`, `DELETE /api/v1/consumption-entries/{id}`) навмисно перехоплюються для блокування мутацій (`405 METHOD_NOT_ALLOWED`, `REQ-F-007`).
2. **DTO-рекорди та схеми (`components/schemas`):**
   - Усі **35/35** схем OpenAPI на 100% збігаються за назвами та JSON-полями (`@JsonProperty`) із 35 Java `record`-класами в пакеті `com.beertracker.dto`.
3. **Коди помилок (`ErrorResponse.code`) та шляхи в документації:**
   - Перевірено всі 71 `ApiException`-викиди у сервісах/контролерах та [`GlobalExceptionHandler`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/exception/GlobalExceptionHandler.java):
     - У [`docs/user-guide.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/user-guide.md) та [`docs/api_design.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/api_design.md) відсутні неіснуючі коди помилок.
     - У [`docs/api_design.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/api_design.md) (§4.2) виявлено та виправлено застарілі скорочені посилання на маршрути (`/merge`, `/moderation/queue`, `DELETE /wishlist-items/{id}`, `DELETE /reviews/{id}`) на фактичні (`/merges`, `/moderation-items/queue`, `DELETE /wishlist-items/{beer_id}`, `DELETE /beers/{id}/reviews`).
   - **Чесно зафіксовані розбіжності між історичними документами / OpenAPI та поточним кодом:**
     - Коди `CONSUMPTION_TIME_BEFORE_REGISTRATION` ([`ConsumptionService`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/service/ConsumptionService.java)), `INVALID_ABV_RANGE` ([`BeerService`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/service/BeerService.java)) та `INVALID_BARCODE_FORMAT` ([`BeerService`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/service/BeerService.java), [`BeerVersionService`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/service/BeerVersionService.java)) реалізовані в коді й описані в [`README.md`](file:///D:/uni/4-year/ПІС/beer-tracker/README.md) / [`docs/user-guide.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/user-guide.md) / [`docs/api_design.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/api_design.md), але відсутні в текстових прикладах замороженого [`api/openapi.yaml`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml).
     - В історичному адендумі [`docs/srs_addendum.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/srs_addendum.md) §3.2 конфлікт активної чернетки іменувався `PENDING_VERSION_CONFLICT`, тоді як фактичний код [`BeerVersionService`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/service/BeerVersionService.java), [`docs/api_design.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/api_design.md) і [`docs/user-guide.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/user-guide.md) використовують `CONCURRENT_DRAFT_CONFLICT`.
4. **Javadoc HTML та Checkstyle:**
   - Усі **91/91** основних Java-файлів мають згенеровані HTML-сторінки в `target/reports/apidocs` (`0` відсутніх файлів, `0` Checkstyle-порушень).
5. **Гіперпосилання у документації:**
   - Перевірено всі `file:///`-посилання у `README.md`, `docs/architecture.md`, `docs/user-guide.md`, `spec/traceability-matrix.md` — `0` неробочих посилань.

---

## 3. Жива перевірка UI та сценаріїв користувача (`target/beer-tracker-1.0-SNAPSHOT.jar`, Крок 10)

У ізольованому живому запуску застосунку (`java -jar target/beer-tracker-1.0-SNAPSHOT.jar --server.port=18080`) виконано **39 наскрізних перевірок** (`curl.exe` + HTTP-клієнт) — **39/39 PASS**:

| Група перевірок | Що перевірено вживу | Результат |
| :--- | :--- | :--- |
| **Swagger UI & OpenAPI** | `GET /`, `/swagger`, `/docs` $\rightarrow$ `302 /swagger-ui.html`; `GET /swagger-ui/index.html` $\rightarrow$ `200`; `GET /openapi.yaml` $\rightarrow$ `200` (побітовий збіг з `api/openapi.yaml`). | **PASS (5/5)** |
| **Приклади з `README.md` (`curl.exe`)** | `POST /auth/register` (`201`), `POST /auth/login` (`200`), `POST /beers` (`201` `UNVERIFIED`), `GET /beers?min_abv=4.0&max_abv=8.0&page=0&size=20` (`200`, `total_elements=1`). | **PASS (4/4)** |
| **Сценарій 1 (`Authentication`)** | Ротація refresh-токена (`200`), блокування повторного використання старого refresh-токена (`401 INVALID_REFRESH_TOKEN`). | **PASS (2/2)** |
| **Сценарії 2–3 (`Catalog` & `Versioning`)** | Неавторизований `POST /beers` (`401`) та `GET /beer-versions` (`401`); дублікат (`409 DUPLICATE_BEER_NAME_CONFLICT`); інвертований ABV (`400 INVALID_ABV_RANGE` + `X-Trace-Id`); створення чернетки (`201`); `403 SELF_VOTING_FORBIDDEN`; голос іншого користувача (`200 OK`); перемикання `verified_only=false/true`. | **PASS (9/9)** |
| **Сценарії 4–5 (`Consumption`, `Wishlist`, `Collection`, `Reviews`)** | `POST /wishlist-items/{id}` (`201`); блокування відгуку без споживання (`403 ERR_CONSUMPTION_REQUIRED`); `POST /consumption-entries` (`201`, `total_volume_ml=1000`, `wishlist_autoremoved=true`, `collection_added=true`); ізоляція між користувачами (`404 CONSUMPTION_ENTRY_NOT_FOUND`); блокування `DELETE /consumption-entries/{id}` (`405 METHOD_NOT_ALLOWED`); автоочищення `Wishlist` та поява в `Tasted Collection`; `POST` (`201`), `PUT` (`200`), `DELETE` (`204`) відгуку зі скиданням `rating_avg` у `null`. | **PASS (10/10)** |
| **Сценарії 6–7 (`Analytics` & `MODERATOR`)** | `GET /streaks` (`current_streak=1`), `GET /analytics/consumption` (`24` погодинних відра); `POST /merges` (`200 MERGED`), `GET /moderation-items/queue` (`200`), `DELETE /beers/{id}` (`204 No Content`), наступний `GET /beers/{id}` $\rightarrow$ `404 BEER_NOT_FOUND`, збереження `"Видалений продукт"` у `GET /consumption-entries/{id}`. | **PASS (6/6)** |
| **Живий NDJSON-лог** | Наявність подій `catalog_merge_executed` (з `moderator_id`) і `security_violation_audit`; відсутність застарілого попередження `HHH90000025` (`H2Dialect`). | **PASS (3/3)** |

---

## 4. Автоматичний аудит матриці простежуваності (Крок 12)

Автоматизований Python-аудит файлу [`spec/traceability-matrix.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/traceability-matrix.md) порівняно з [`spec/SRS.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md), [`spec/acceptance_criteria_bdd.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/acceptance_criteria_bdd.md), [`spec/srs_addendum_002.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/srs_addendum_002.md), [`docs/implementation_plan.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/implementation_plan.md), [`tests/api_mock_scenarios.sh`](file:///D:/uni/4-year/ПІС/beer-tracker/tests/api_mock_scenarios.sh), [`api/openapi.yaml`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) та вихідним кодом виявив на першому прогоні 3 невідповідності (усі усунено перед повторним аудитом):

1. Вилучено неіснуючий критерій `AC-013.3` у рядку `REQ-F-013` (у [`spec/acceptance_criteria_bdd.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/acceptance_criteria_bdd.md) визначено лише `AC-013.1` та `AC-013.2`).
2. Додано пропущену колонку `Documentation Section` для рядків `REQ-NF-001`, `REQ-NF-002`, `REQ-NF-003` (посилання на чесний статус про неперевірене навантаження).
3. Виправлено помилкове ім'я методу `rejectConsumptionMutation` у рядку `REQ-F-007` на фактичні методи [`ConsumptionController`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/controller/ConsumptionController.java): `rejectPut`, `rejectPatch`, `rejectDelete`.

**Підсумок повторного аудиту матриці:**
- Рядків таблиці: `27` (`26` вимог `REQ-F-001..015`, `REQ-NF-001..011` + `UI-OPENAPI-001`).
- Порожніх комірок або зсувів колонок: `0`.
- Пропущених / фантомних `REQ-ID`: `0 / 0`.
- Пропущених / фантомних `AC-ID` (з `53`): `0 / 0`.
- Пропущених / невикористаних `BDD-SCN-ID` (з `6`): `0 / 0`.
- Пропущених / невикористаних `API-POS/NEG` mock-сценаріїв (з `20`): `0 / 0`.
- Пропущених тікетів (`#3..#41`): `0`.
- Тестів-сиріт (`9` класів, `44` `@Test`-методи): `0`.
- Неіснуючих класів, методів контролерів, схем OpenAPI або файлових посилань: `0`.

---

## 5. Статус вимог продуктивності (`REQ-NF-001`, `REQ-NF-002`, `REQ-NF-003`)

Спеціалізоване навантажувальне тестування (100 RPS читання каталогу, 30 RPS на 10 000 записів аналітики, 50 Write RPS споживання) **не проводилося**. Це явно відображено у [`README.md`](file:///D:/uni/4-year/ПІС/beer-tracker/README.md), [`docs/architecture.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/architecture.md) §4 та [`spec/traceability-matrix.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/traceability-matrix.md).
