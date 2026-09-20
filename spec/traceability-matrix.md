# Initial Traceability Matrix

| Requirement ID | Requirement Summary | Acceptance Criteria ID | BDD Scenario ID | Ticket ID | Test ID | Implementation Status |
|---|---|---|---|---|---|---|
| **REQ-F-001** | Реєстрація нового продукту з перевіркою зрілості акаунта та лімітів (Anti-Abuse Gate) | AC-001.1, AC-001.2, AC-001.3 | BDD-SCN-005 | [TODO] | [TODO] | Specified |
| **REQ-F-002** | Дедуплікація продуктів за штрих-кодом (EAN-13/UPC) та нормалізованою назвою | AC-002.1, AC-002.2 | BDD-SCN-004 | [TODO] | [TODO] | Specified |
| **REQ-F-003** | Краудсорсинговий консенсус: підтвердження (10 Upvotes) та відхилення чернеток | AC-003.1, AC-003.2, AC-003.3, AC-003.4 | BDD-SCN-002 | [TODO] | [TODO] | Specified |
| **REQ-F-004** | Двоверсійне відображення каталогу з перемикачем "Тільки верифіковані" | AC-004.1, AC-004.2 | BDD-SCN-002 | [TODO] | [TODO] | Specified |
| **REQ-F-005** | Анонімність автора продукту (приховування User ID у публічних інтерфейсах/API) | AC-005.1, AC-005.2 | N/A | [TODO] | [TODO] | Specified |
| **REQ-F-006** | Кількісний облік споживання з 60-секундним буфером м'якого скасування (Soft-Undo) | AC-006.1, AC-006.2, AC-006.3 | BDD-SCN-001, BDD-SCN-003 | [TODO] | [TODO] | Specified |
| **REQ-F-007** | Офлайн-буферизація подій споживання та синхронізація після відновлення мережі | AC-007.1, AC-007.2 | N/A | [TODO] | [TODO] | Specified |
| **REQ-F-008** | Автоматичне формування Колекції та перенесення з Вішліста при першому споживанні | AC-008.1, AC-008.2, AC-008.3 | BDD-SCN-001, BDD-SCN-003 | [TODO] | [TODO] | Specified |
| **REQ-F-009** | Оцінювання (1.0–5.0, крок 0.5) та відгуки виключно на продукти з Колекції | AC-009.1, AC-009.2, AC-009.3 | N/A | [TODO] | [TODO] | Specified |
| **REQ-F-010** | Локалізована часова аналітика та розрахунок серій (Streaks) за IANA-таймзоною | AC-010.1, AC-010.2, AC-010.3 | BDD-SCN-001 | [TODO] | [TODO] | Specified |
| **REQ-F-011** | Формування топів за частотою та об'ємом споживання (без урахування оцінок) | AC-011.1, AC-011.2 | N/A | [TODO] | [TODO] | Specified |
| **REQ-F-012** | Адміністративне видалення спаму з каскадною заміною на Tombstone-плейсхолдер | AC-012.1, AC-012.2 | N/A | [TODO] | [TODO] | Specified |
| **REQ-NF-001**| Продуктивність читання каталогу: p95 $\le 200\text{ мс}$ при навантаженні 100 RPS | AC-NF-001.1 | N/A | [TODO] | [TODO] | Specified |
| **REQ-NF-002**| Продуктивність запису споживання: p99 $\le 100\text{ мс}$ при навантаженні 50 RPS | AC-NF-002.1 | N/A | [TODO] | [TODO] | Specified |
| **REQ-NF-003**| Швидкість розрахунку аналітики: p95 $\le 500\text{ мс}$ для 10 000 записів при 20 RPS | AC-NF-003.1 | N/A | [TODO] | [TODO] | Specified |
| **REQ-NF-004**| Безпека облікових даних: хешування паролів за bcrypt ($\ge 12$) або Argon2id | AC-NF-004.1 | N/A | [TODO] | [TODO] | Specified |
| **REQ-NF-005**| Безпека передачі даних: TLS 1.3 / HTTPS та мультитенантна ізоляція користувачів | AC-NF-005.1 | N/A | [TODO] | [TODO] | Specified |
| **REQ-NF-006**| Захист від Sybil-атак: ліміт 5 реєстрацій/IP/год та HTTP 403 для незрілих акаунтів | AC-NF-006.1 | BDD-SCN-005 | [TODO] | [TODO] | Specified |
| **REQ-NF-007**| Приховування ідентифікатора автора продукту в усіх публічних DTO | AC-NF-007.1 | N/A | [TODO] | [TODO] | Specified |
| **REQ-NF-008**| Надійність клієнтського сховища: 0% втрат даних черги в IndexedDB/SQLite | AC-NF-008.1 | N/A | [TODO] | [TODO] | Specified |
| **REQ-NF-009**| Ідемпотентність синхронізації споживань на основі клієнтського UUID події | AC-NF-009.1 | N/A | [TODO] | [TODO] | Specified |
| **REQ-NF-010**| Незмінність логу в БД (заборона UPDATE/DELETE) та атомарні ACID транзакції | AC-NF-010.1 | N/A | [TODO] | [TODO] | Specified |
