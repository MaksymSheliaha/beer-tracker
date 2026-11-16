# Beer Tracker Backend API

Серверна система (**Headless REST API** із вбудованим **Swagger UI**) для кількісного обліку споживання пива, ведення персонального дегустаційного щоденника (відгуки, вішліст, дегустаційна колекція), розрахунку тайм-серійної аналітики та краудсорсингового каталогу сортів на wiki-засадах.

Реалізовано за методологією **Specification-Driven Development (SDD)**, **API-First (`OpenAPI 3.0.3`)** та **Test-Driven Development (TDD)**.

---

## Технологічний стек та залежності

| Компонент | Версія / технологія |
| :--- | :--- |
| Мова та платформа | **Java 25** |
| Фреймворк | **Spring Boot 3.4.5** (`spring-boot-starter-web`, `data-jpa`, `validation`) |
| База даних | **H2 Database 2.3.x** (in-memory `jdbc:h2:mem:beertracker`) |
| Безпека та криптографія | Stateless **HMAC-SHA256 JWT**, `spring-security-crypto` (**BCrypt**, cost `12`) |
| API-документація | **OpenAPI 3.0.3** ([`api/openapi.yaml`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml)) + **SpringDoc Swagger UI 2.8.5** |
| Логування | **SLF4J + Logback** із `logstash-logback-encoder 8.0` (NDJSON + MDC `trace_id`) |
| Збірка та якість коду | **Apache Maven 3.9+**, `maven-checkstyle-plugin 3.6.0` (Checkstyle `10.21.4`), `maven-javadoc-plugin 3.11.2` |

---

## Передумови (Prerequisites)

- **JDK 25** (`java -version`)
- **Apache Maven 3.9+** (`mvn -version`)
- *(Опціонально для лінтингу OpenAPI та mock-сценаріїв)* **Node.js 18+** / `npx` (`@redocly/cli`, `@stoplight/prism-cli`) і **Git Bash** / `curl` / `python3`.

---

## Конфігурація

Основні параметри налаштовані у [`src/main/resources/application.properties`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/resources/application.properties):

| Властивість / Змінна | Значення за замовчуванням | Призначення |
| :--- | :--- | :--- |
| `server.port` | `8080` | HTTP-порт сервісу (можна перевизначити через `--server.port=...` або `SERVER_PORT`) |
| `spring.datasource.url` | `jdbc:h2:mem:beertracker;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` | Реляційна H2-база у пам'яті |
| `spring.jpa.hibernate.ddl-auto` | `update` | Автоматичне створення схеми JPA при старті |
| `security.jwt.secret` | `beer-tracker-default-hmac-sha256-secret-key-2026-sdd-tdd` | Секретний ключ підпису HS256 JWT *(обов'язково змініть у production через `SECURITY_JWT_SECRET` або `--security.jwt.secret=...`)* |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Маршрут вбудованого Swagger UI |
| `springdoc.swagger-ui.url` | `/openapi.yaml` | Джерело контракту OpenAPI 3.0.3 |

> **Важливо щодо збереження стану:** Система використовує H2 у пам'яті (`in-memory`), а компонент [`DatabaseStateInitializer`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/java/com/beertracker/repository/DatabaseStateInitializer.java) очищує всі таблиці під час запуску контексту. Усі дані зберігаються лише в межах поточного процесу до зупинки сервера.

---

## Збірка, запуск та інтерактивна документація

### 1. Запуск у режимі розробки
```bash
mvn spring-boot:run
```

### 2. Збірка виконуваного JAR та запуск
```bash
mvn clean package -DskipTests
java -jar target/beer-tracker-1.0-SNAPSHOT.jar
```

Із перевизначенням JWT-секрету та порту:
```bash
java -jar target/beer-tracker-1.0-SNAPSHOT.jar --server.port=8080 --security.jwt.secret="replace-with-strong-secret-key"
```

### 3. Доступ до Swagger UI та OpenAPI-контракту
Після запуску сервісу на `http://localhost:8080`:
- **Swagger UI:** `http://localhost:8080/swagger-ui.html` (автоматичні редіректи також з `/`, `/swagger`, `/docs`)
- **Специфікація OpenAPI 3.0.3 (YAML):** `http://localhost:8080/openapi.yaml`

---

## Швидка перевірка роботи API (`curl`)

1. **Реєстрація користувача:**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"StrongPassword123!","timezone":"Europe/Kyiv"}'
```

2. **Автентифікація та отримання Access Token:**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"StrongPassword123!"}'
```

3. **Перегляд каталогу пива з фільтром за міцністю (`min_abv`, `max_abv`):**
```bash
curl -s "http://localhost:8080/api/v1/beers?min_abv=4.0&max_abv=8.0&page=0&size=20"
```

4. **Створення сорту пива (з Bearer-токеном з кроку 2):**
```bash
curl -s -X POST http://localhost:8080/api/v1/beers \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Mova Dark Stout","brewery":"Mova Brewing Co.","style":"Oatmeal Stout","abv":5.8,"beer_type":"PACKAGED","country":"UA","packagings":[{"volume_ml":500,"package_type":"CAN","barcode":"4820202600018"}]}'
```

Детальні сценарії користувача та модератора наведено в [Посібнику користувача (`docs/user-guide.md`)](file:///D:/uni/4-year/ПІС/beer-tracker/docs/user-guide.md).

---

## Тестування, статичний аналіз і генерація документації

| Перевірка | Команда | Що перевіряє |
| :--- | :--- | :--- |
| Інтеграційні тести | `mvn test` | Повний набір інтеграційних тестів Spring Boot + `MockMvc` |
| Перевірка стилю коду | `mvn checkstyle:check` | Правила оформлення з [`config/checkstyle/checkstyle.xml`](file:///D:/uni/4-year/ПІС/beer-tracker/config/checkstyle/checkstyle.xml) для `src/main` і `src/test` |
| Повна верифікація збірки | `mvn clean verify` | Компіляція, тести Surefire та Checkstyle-перевірка |
| Генерація Javadoc HTML | `mvn javadoc:javadoc` | Генерує API-довідник коду в `target/reports/apidocs/index.html` (`doclint=all,-missing`) |
| Лінтинг OpenAPI | `npx @redocly/cli lint api/openapi.yaml` | Синтаксична та контрактна валідність OpenAPI 3.0.3 |
| Mock-сценарії Prism | `npx @stoplight/prism-cli mock api/openapi.yaml -p 4010` і `./tests/api_mock_scenarios.sh` | Перевірка 20 контрактних сценаріїв проти mock-сервера |

---

## Структуроване логування

Конфігурація [`logback-spring.xml`](file:///D:/uni/4-year/ПІС/beer-tracker/src/main/resources/logback-spring.xml) формує події у форматі **NDJSON** одночасно у `stdout` та файл `logs/beer-tracker.json.log` (ротація 100 МБ / 30 діб / 10 ГБ). Кожен запис збагачується полями MDC: `trace_id` (дублюється у HTTP-заголовку `X-Trace-Id` і тілі помилки `ErrorResponse.traceId`), `user_id`, `client_ip` та `status`.

---

## Відомі обмеження та результати перевірки навантаження

> **Статус перевірки продуктивності (`REQ-NF-001`, `REQ-NF-002`, `REQ-NF-003`):**
> **Навантаження не перевірялося.** Проєкт містить функціональні та конкурентні інтеграційні тести (`MockMvc`), але спеціалізоване навантажувальне тестування (перевірка цільових порогів `100 RPS` при `p95 <= 100 мс` для каталогу, `30 RPS` на `10 000` записів при `p95 <= 250 мс` для аналітики та `50 Write RPS` при `p95 <= 150 мс` для журналу споживання) **не проводилося**.

Інші документовані відхилення від базової SRS (використання in-memory H2 замість PostgreSQL/Testcontainers та зберігання URI зображень без S3) зафіксовано в [архітектурній документації (`docs/architecture.md`)](file:///D:/uni/4-year/ПІС/beer-tracker/docs/architecture.md).

---

## Пов'язана документація проєкту

- Контракт REST API: [`api/openapi.yaml`](file:///D:/uni/4-year/ПІС/beer-tracker/api/openapi.yaml) та [`docs/api_design.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/api_design.md)
- Архітектура та супровід: [`docs/architecture.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/architecture.md)
- Посібник користувача: [`docs/user-guide.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/user-guide.md)
- Специфікація вимог та глосарій: [`spec/SRS.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/SRS.md), [`docs/srs_addendum.md`](file:///D:/uni/4-year/ПІС/beer-tracker/docs/srs_addendum.md), [`spec/srs_addendum_002.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/srs_addendum_002.md), [`spec/domain_glossary.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/domain_glossary.md)
- Матриця простежуваності: [`spec/traceability-matrix.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/traceability-matrix.md)
