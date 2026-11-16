# Підсумковий журнал SDD-циклу (SDD Cycle Summary)

**Проєкт:** Beer Tracker Backend API (`com.beertracker`)  
**Гілка розробки:** `dev`  
**Релізний тег:** `v1.0.0-r1`  
**Період циклу:** Жовтень 2026 — Листопад 2026 (Лабораторні роботи №1–6)

---

## 1. Еволюція проєкту за фазами SDD

| Фаза SDD | Ключові артефакти та дії | Контрольний статус |
| :--- | :--- | :--- |
| **1. Задум та `SKED`-інтерв'ю** | Формулювання ідеї в [`spec/initial_concept.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/initial_concept.md); узгодження архітектурних рішень (`Read Committed`, append-only споживання, крауд-консенсус `N=5`, IANA day boundary `[00:00, 24:00)`) у [`logs/sked-summary.md`](file:///D:/uni/4-year/ПІС/beer-tracker/logs/sked-summary.md) та [`logs/conflict_resolution.md`](file:///D:/uni/4-year/ПІС/beer-tracker/logs/conflict_resolution.md). | Завершено |
| **2. Специфікація (`SRS` & `SPEC-GATE`)** | Розробка IEEE 830 SRS v2.1.0 ([`spec/SRS.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md): `REQ-F-001..014`, `REQ-NF-001..011`), глосарію ([`spec/domain_glossary.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/domain_glossary.md)) та приймальних BDD-критеріїв ([`spec/acceptance_criteria_bdd.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/acceptance_criteria_bdd.md)). Рішення у [`logs/spec-gate-decision.md`](file:///D:/uni/4-year/ПІС/beer-tracker/logs/spec-gate-decision.md). | `SPEC-GATE`: **GO** |
| **3. API-First проєктування (`API-GATE`)** | OpenAPI 3.0.3 контракт ([`api/openapi.yaml`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml)), проєктування та Prism-тестування 20 контрактних сценаріїв ([`docs/api_design.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/api_design.md), [`tests/api_mock_scenarios.sh`](file:///D:/uni/4-year/ПІС/beer-tracker/tests/api_mock_scenarios.sh)), перший адендум **`SRS-ADD-001`** ([`docs/srs_addendum.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/srs_addendum.md)). Рішення у [`docs/api_gate_report.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/api_gate_report.md). | `API-GATE`: **GO** |
| **4. Планування (`PLAN-GATE`)** | Декомпозиція на 38 задач `#3..#40` у [`docs/implementation_plan.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/implementation_plan.md), життєвий цикл ([`docs/task_lifecycle.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/task_lifecycle.md)) та реєстр ризиків ([`docs/risk_register.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/risk_register.md)). Рішення у [`logs/plan-gate-decision.md`](file:///D:/uni/4-year/ПІС/beer-tracker/logs/plan-gate-decision.md). | `PLAN-GATE`: **GO** |
| **5. Базова TDD-реалізація + `SRS-ADD-002`** | Ітеративна реалізація Етапів 1–8 (`#4..#40`), адендум **`SRS-ADD-002`** ([`spec/srs_addendum_002.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/srs_addendum_002.md), тікет `#41` — фільтрація `min_abv`/`max_abv`, `REQ-F-015`), [`logs/spec-addendum-gate-decision.md`](file:///D:/uni/4-year/ПІС/beer-tracker/logs/spec-addendum-gate-decision.md). | Завершено |
| **6. Архітектурна еволюція (`persistence` + Swagger UI)** | Перехід зі сховища в оперативних `ConcurrentHashMap` на реляційну БД **H2 + Spring Data JPA** (`2c1f563`); інтеграція вбудованого **Swagger UI** (`48f1177`); вирівнювання query-параметрів OpenAPI (`d342f2d`). | Завершено |
| **7. Фіналізація, конвенції, безпека та документування (Lab 6)** | Наведено нижче в розділі 2. | Завершено |

---

## 2. Ключові виправлення та результати етапу Lab 6

Під час аудиту фактичної системи проти специфікації SRS, глосарію та контракту API було виявлено й усунено (за погодженням із розробником-людиною) такі критичні розбіжності:

1. **Авторизація й ізоляція даних (`BearerAuth`, `REQ-NF-004`, `REQ-NF-005`):**
   - Увімкнено обов'язкову перевірку Bearer JWT (`401 Unauthorized`) на `POST /api/v1/beers`, `GET /api/v1/beer-versions`, `POST /api/v1/consumption-entries`, `GET /api/v1/consumption-entries` та `GET /api/v1/consumption-entries/{id}` через спільний компонент [`JwtRequestAuthenticator`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/security/JwtRequestAuthenticator.java).
   - Реалізовано сувору приватну ізоляцію журналу споживання за `userId`: звернення іншого користувача до `GET /api/v1/consumption-entries/{id}` повертає `404 CONSUMPTION_ENTRY_NOT_FOUND`.
2. **Бізнес-правила, ACID-транзакційність та аудит логів:**
   - Впроваджено [`ReadCommittedTxExecutor`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/service/ReadCommittedTxExecutor.java) (`ISOLATION_READ_COMMITTED`), щоб коміти БД гарантовано завершувалися всередині per-resource `synchronized`-секцій.
   - У [`BeerVersionService`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/service/BeerVersionService.java) додано перевірку сумісності `PACKAGED`/`DRAFT` (`AC-001.4`) та формату штрих-коду.
   - У [`ConsumptionService`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/service/ConsumptionService.java) додано валідацію `consumed_at < User.created_at` (`422 CONSUMPTION_TIME_BEFORE_REGISTRATION`, `AC-006.3`), розрахунок `totalVolumeSoFar` у межах одного користувача та `findActiveById`.
   - У [`MergeService`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/service/MergeService.java) уніфіковано назву події логу `catalog_merge_executed` із додаванням `moderator_id` (`REQ-NF-011`).
   - У [`ReviewService`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/service/ReviewService.java) виправлено перерахунок рейтингу при видаленні останнього відгуку (`ratingAvg = null`).
   - Вилучено мертвий клас `BeerEntity.java` і застаріле налаштування `H2Dialect` (`HHH90000025`).
3. **Стиль коду, термінологія та документація:**
   - Застосовано Checkstyle `10.21.4` (`0` порушень на `91` основному + `10` тестових файлах), усунено дублювання у контролерах/сервісах/маперах ([`PaginationHelper`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/validation/PaginationHelper.java), `BeerMapper.mergeWithVersionPayload`), додано Javadoc (`target/reports/apidocs/index.html`), термінологію вирівняно з [`spec/domain_glossary.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/domain_glossary.md).
   - Створено/оновлено [`README.md`](file:///D:/uni/4-year/ПІС/beer-tracker/README.md), [`docs/architecture.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/architecture.md), [`docs/user-guide.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/user-guide.md), [`docs/api_design.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/api_design.md), [`docs/doc-verification-report.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/doc-verification-report.md) та завершено аудит [`spec/traceability-matrix.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/traceability-matrix.md).

---

## 3. Валідація системи відносно початкового задуму ([`spec/initial_concept.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/initial_concept.md))

Повертаючись до початкової мети проєкту (*автоматизація кількісного обліку спожитого пива та ведення персонального дегустаційного щоденника з оцінками, колекцією та аналізом*):

| Ключовий напрям задуму | Оцінка відповідності реалізації | Зауваження та межі MVP `v1.0.0-r1` |
| :--- | :--- | :--- |
| **Quantitative consumption tracking** | **Повністю вирішено** | Реалізовано незмінний (`Append-Only`) журнал споживання із серверним підрахунком мл, часовими рамками, IANA-аналітикою за годинами/періодами та стріками. |
| **Collection formation** | **Повністю вирішено** | Поєднання автоматизованої `Tasted Collection`, транзакційного `Wishlist` і wiki-каталогу сортів (`Beer` + `BeerVersion` + крауд-голосування `N=5` + фільтрація `min_abv`/`max_abv`). |
| **Rating and reviewing** | **Повністю вирішено** | Рецензування суворо прив'язане до реального споживання (`403 ERR_CONSUMPTION_REQUIRED`), оцінки `1.0..5.0` з кроком `0.5`, точне агрегування `rating_avg` та `reviews_count`. |
| **Review database analysis** | **Вирішено на рівні агрегованих оцінок та персональної аналітики споживання** | Реалізовано агрегований рейтинг каталогу (`rating_avg`, `reviews_count`), персональний рейтинг у `Tasted Collection`, архівацію конфліктів під час `Merge` і 24-годинну аналітику споживання. Повнотекстовий NLP/сентимент-аналіз текстів відгуків (`review_text`) не входив до SRS і зафіксований поза обсягом поточного SDD-циклу. |
| **Нефункціональні очікування (БД, S3, навантаження)** | **Реалізовано частково (із явними задокументованими обмеженнями)** | Замість PostgreSQL/Testcontainers/S3 застосовано H2 In-Memory + зовнішні URI `image_url`; **навантажувальні тести (`REQ-NF-001..003`) не проводилися**. Ці відхилення явно задокументовані у `README.md`, `docs/architecture.md` і `FINAL-GATE` без приховування. |
