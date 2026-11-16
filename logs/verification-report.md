# Підсумковий звіт про верифікацію системи (Final Verification Report)

**Дата:** 2026-11-16  
**Гілка:** `dev`  
**Платформа перевірки:** Java 25, Spring Boot 3.4.5, H2 In-Memory DB, Apache Maven 3.9.11, Apache Checkstyle `10.21.4`

---

## 1. Статичний аналіз, компіляція та генерація Javadoc

- **Конвенція оформлення коду:** Google Java Style (4 пробіли, довжина рядка $\le 120$, фігурні дужки, Javadoc для публічних типів і методів), зафіксована у [`.editorconfig`](file:///D:/uni/4-year/ПІС/beer-tracker/.editorconfig) та [`config/checkstyle/checkstyle.xml`](file:///D:/uni/4-year/ПІС/beer-tracker/config/checkstyle/checkstyle.xml).
- **`mvn clean verify javadoc:javadoc`:**
  - Скомпільовано `91` основний Java-файл та `10` тестових Java-файлів.
  - **`maven-checkstyle-plugin 3.6.0` (`checkstyle:check`):** `0` порушень (main + test sources).
  - **`maven-javadoc-plugin 3.11.2` (`javadoc:javadoc`, `<doclint>all,-missing</doclint>`):** `0` помилок, `0` попереджень; згенеровано `220` HTML-сторінок у `target/reports/apidocs/com/beertracker` (`target/reports/apidocs/index.html`).
  - Зібрано виконуваний артефакт `target/beer-tracker-1.0-SNAPSHOT.jar`.

---

## 2. Інтеграційне тестування (`mvn test` / Surefire)

**Загальний результат:** **Tests run: 44, Failures: 0, Errors: 0, Skipped: 0 (100% PASS)**.

| Тестовий клас | К-сть тестів | Покриті вимоги та перевірки |
| :--- | :---: | :--- |
| [`AdminMergeAndModerationIntegrationTest`](file:///D:/uni/4-year/ПІС/beer-tracker/src/test/java/com/beertracker/AdminMergeAndModerationIntegrationTest.java) | 3 | `REQ-F-010`, `REQ-F-011`, `REQ-F-013`, `REQ-F-014`, `REQ-NF-007`, `REQ-NF-011` (`Placeholder Beer`, транзакційний Merge із міграцією FK та архівацією відгуків, верифікація/відхилення та черга модерації). |
| [`AnalyticsAndStreaksIntegrationTest`](file:///D:/uni/4-year/ПІС/beer-tracker/src/test/java/com/beertracker/AnalyticsAndStreaksIntegrationTest.java) | 3 | `REQ-F-012`, `REQ-NF-002` *(функц.)* (IANA Day Boundaries `[00:00, 24:00)`, скидання `current_streak` при пропуску доби, 24-елементний погодинний профіль). |
| [`AuthIntegrationTest`](file:///D:/uni/4-year/ПІС/beer-tracker/src/test/java/com/beertracker/AuthIntegrationTest.java) | 6 | `REQ-NF-004`, `REQ-NF-006`, `REQ-NF-008`, `REQ-NF-011` (реєстрація `201` без витоку пароля, дублікат email `409`, IANA/пароль `422`, `BCrypt` вхід `200`, хибні дані `401`, одноразова ротація refresh-токена і `401` на повтор). |
| [`BeerCatalogIntegrationTest`](file:///D:/uni/4-year/ПІС/beer-tracker/src/test/java/com/beertracker/BeerCatalogIntegrationTest.java) | 11 | `REQ-F-001`, `REQ-F-002`, `REQ-F-003`, `REQ-F-015`, `REQ-NF-001` *(функц.)*, `REQ-NF-004`, `REQ-NF-008`, `REQ-NF-010` (`401` без токена на `POST /beers`, пагінація та `400 INVALID_PAGE_SIZE`, `VOLUME_OUT_OF_BOUNDS`, `DRAFT`/`PACKAGED` несумісність `AC-001.3`/`AC-001.4`, дублікат штрих-коду та `(brewery, name)` `409`, `min_abv`/`max_abv` `BDD-SCN-006`, `verified_only`). |
| [`BeerTrackerApplicationTests`](file:///D:/uni/4-year/ПІС/beer-tracker/src/test/java/com/beertracker/BeerTrackerApplicationTests.java) | 1 | `REQ-NF-010`, `REQ-NF-011` (підняття контексту Spring Boot + H2 + запис NDJSON у `logs/beer-tracker.json.log`). |
| [`BeerVersionVotingIntegrationTest`](file:///D:/uni/4-year/ПІС/beer-tracker/src/test/java/com/beertracker/BeerVersionVotingIntegrationTest.java) | 6 | `REQ-F-002`, `REQ-F-003`, `REQ-F-004`, `REQ-F-005`, `REQ-NF-007`, `REQ-NF-009` (чернетки, валідація пакування у версії `AC-001.4`, конфлікт чернеток `409`, заборона само- та повторного голосування, паралельна автоверифікація `N=5` без race conditions, авто-відхилення `REJECTED`). |
| [`ConsumptionLogIntegrationTest`](file:///D:/uni/4-year/ПІС/beer-tracker/src/test/java/com/beertracker/ConsumptionLogIntegrationTest.java) | 7 | `REQ-F-006`, `REQ-F-007`, `REQ-NF-003` *(функц.)*, `REQ-NF-004`, `REQ-NF-005`, `REQ-NF-008`, `REQ-NF-011` (облік споживання `201`, `401` без JWT, `404` крос-користувацька ізоляція, часові рамки включно з `CONSUMPTION_TIME_BEFORE_REGISTRATION` `AC-006.3`, межі `quantity`, блокування `PUT/PATCH/DELETE` `405 METHOD_NOT_ALLOWED`). |
| [`ReviewsAndWishlistIntegrationTest`](file:///D:/uni/4-year/ПІС/beer-tracker/src/test/java/com/beertracker/ReviewsAndWishlistIntegrationTest.java) | 4 | `REQ-F-008`, `REQ-F-009`, `REQ-NF-005`, `REQ-NF-007`, `REQ-NF-008` (`403 ERR_CONSUMPTION_REQUIRED`, крок оцінки `0.5`, CRUD відгуків зі скиданням `rating_avg` у `null` при `0` активних відгуках, транзакційне автоочищення `Wishlist` + поповнення `Tasted Collection`). |
| [`SwaggerUiIntegrationTest`](file:///D:/uni/4-year/ПІС/beer-tracker/src/test/java/com/beertracker/SwaggerUiIntegrationTest.java) | 3 | `UI-OPENAPI-001` (редіректи `/` і `/swagger` $\rightarrow$ `/swagger-ui.html`, HTML Swagger UI `200`, роздача `/openapi.yaml` `200`). |

---

## 3. Жива перевірка, OpenAPI та структуроване JSON-логування

- **OpenAPI 3.0.3 (`api/openapi.yaml`):** `npx --yes @redocly/cli lint api/openapi.yaml` — `0 errors`, `1` очікуване попередження (`no-server-example.com`); SHA-256 ідентичний до `src/main/resources/static/openapi.yaml`.
- **Живий прогін виконуваного JAR (`java -jar target/beer-tracker-1.0-SNAPSHOT.jar --server.port=18080`):** **39/39** перевірок PASS (редіректи Swagger UI, `curl.exe` команди з [`README.md`](file:///D:/uni/4-year/ПІС/beer-tracker/README.md), усі 7 сценаріїв [`docs/user-guide.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/user-guide.md)).
- **Перевірені події NDJSON у коді та живих логах (`stdout` / `logs/beer-tracker.json.log`):**
  - `trace_id` та `client_ip` (`TraceIdFilter`), HTTP статус і аудитні події:
  - `catalog_search`, `beer_created`, `beer_version_created`, `version_verification_completed`, `consumption_entry_created`, `catalog_merge_executed` (включно з `moderator_id`), `beer_soft_deleted`, `moderation_decision_processed`, `review_created`, `review_updated`, `review_archived`, `wishlist_item_added`, `wishlist_item_removed`, `streaks_calculated`, `analytics_calculated`, `user_registered`, `user_authenticated`, `token_refreshed`, `security_violation_audit`.
  - Попередження Hibernate `HHH90000025` (`H2Dialect`) повністю усунуто.

---

## 4. Автоматичний аудит матриці простежуваності та документації

Повний звіт див. у [`docs/doc-verification-report.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/doc-verification-report.md). Аудит [`spec/traceability-matrix.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/traceability-matrix.md) підтвердив: `27` рядків (`26` `REQ-*` + `UI-OPENAPI-001`), `0` порожніх комірок, `0` пропущених/невикористаних вимог (`26`), `AC` (`53`), `BDD-SCN` (`6`), mock-сценаріїв (`20`), тікетів (`#3..#41`) та тестів (`9` класів, `44` методи).

---

## 5. Відомі неперевірені вимоги (Explicit Exclusions)

- **`REQ-NF-001`, `REQ-NF-002`, `REQ-NF-003`:** Навантажувальні випробування (`100 RPS` каталогу, `30 RPS` / `10 000` записів аналітики, `50 Write RPS` споживання) **не проводилися**.
