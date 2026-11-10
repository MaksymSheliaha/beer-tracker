# Звіт комплексної верифікації системи (Ticket #39)

- **Дата та час виконання:** 2026-11-10T21:12:25+02:00
- **Команда збору та верифікації:** `mvn clean verify`
- **Статус збірки:** `BUILD SUCCESS` (Total time: 33.121 s)
- **Артефакт збірки:** `target/beer-tracker-1.0-SNAPSHOT.jar`

## 1. Підсумковий звіт виконання тестів

| Тестовий клас | Кількість тестів | Failures | Errors | Skipped | Статус |
|---|---|---|---|---|---|
| `BeerTrackerApplicationTests` | 1 | 0 | 0 | 0 | PASS (GREEN) |
| `BeerCatalogIntegrationTest` | 10 | 0 | 0 | 0 | PASS (GREEN) |
| `ConsumptionLogIntegrationTest` | 5 | 0 | 0 | 0 | PASS (GREEN) |
| `AuthIntegrationTest` | 4 | 0 | 0 | 0 | PASS (GREEN) |
| `BeerVersionVotingIntegrationTest` | 4 | 0 | 0 | 0 | PASS (GREEN) |
| `ReviewsAndWishlistIntegrationTest` | 4 | 0 | 0 | 0 | PASS (GREEN) |
| `AdminMergeAndModerationIntegrationTest` | 4 | 0 | 0 | 0 | PASS (GREEN) |
| `AnalyticsAndStreaksIntegrationTest` | 2 | 0 | 0 | 0 | PASS (GREEN) |
| **ВСЬОГО** | **34** | **0** | **0** | **0** | **100% GREEN** |

## 2. Перевірка валідності структурованих JSON-логів (`REQ-NF-010`, `REQ-NF-011`)

- **Файл логування:** `logs/beer-tracker.json.log`
- **Формат:** NDJSON (Newline-Delimited JSON) через `LogstashEncoder` (`logback-spring.xml`)
- **Кількість перевірених записів:** 4298 валідних JSON-рядків
- **Обов'язкові поля MDC та контексту:** `timestamp` (ISO-8601), `level`, `logger`, `thread`, `message`, `trace_id` (UUID v4), `client_ip`, `user_id`, `status`, `event`
- **Перевірені доменні події (`event`):**
  - `beer_created`, `catalog_search` (включно з `min_abv`, `max_abv`, `hits_count`, `duration_ms`)
  - `consumption_entry_created` (`consumption_id`, `volume_ml`, `quantity`, `total_volume_so_far_ml`, `wishlist_autoremoved`, `collection_added`)
  - `user_registered`, `user_logged_in`, `token_refreshed` (без витоку `password` чи `password_hash`)
  - `beer_version_created`, `beer_version_voted`, `beer_version_auto_transitioned`
  - `review_created`, `review_updated`, `review_archived`
  - `beer_soft_deleted`, `beer_merged`, `beer_version_verified_by_moderator`, `beer_version_rejected_by_moderator`, `moderation_decision_applied`
  - `analytics_consumption_calculated`, `streaks_calculated`
