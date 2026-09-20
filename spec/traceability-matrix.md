# Initial Traceability Matrix

| Requirement ID | Requirement Summary | Acceptance Criteria ID | BDD Scenario ID | Ticket ID | Test ID | Implementation Status |
|---|---|---|---|---|---|---|
| REQ-F-001 | Реєстрація нового продукту в каталозі | AC-001.1, AC-001.2, AC-001.3 | N/A | [TODO] | [TODO] | Specified |
| REQ-F-002 | Превентивна дедуплікація каталогу за назвою та штрих-кодом | AC-002.1, AC-002.2 | BDD-SCN-004 | [TODO] | [TODO] | Specified |
| REQ-F-003 | Модифікація продукту через створення версії (BeerVersion) | AC-003.1, AC-003.2 | N/A | [TODO] | [TODO] | Specified |
| REQ-F-004 | Краудсорсингове голосування за версію (Confirm/Reject) | AC-004.1, AC-004.2, AC-004.3 | BDD-SCN-002 | [TODO] | [TODO] | Specified |
| REQ-F-005 | Автоматична верифікація та відхилення версій за консенсусом (N=5) | AC-005.1, AC-005.2, AC-005.3 | BDD-SCN-002 | [TODO] | [TODO] | Specified |
| REQ-F-006 | Реєстрація факту споживання (Append-Only Log) | AC-006.1, AC-006.2, AC-006.3 | BDD-SCN-001 | [TODO] | [TODO] | Specified |
| REQ-F-007 | Заборона зміни та видалення логів споживання (405 Method Not Allowed) | AC-007.1, AC-007.2 | BDD-SCN-005 | [TODO] | [TODO] | Specified |
| REQ-F-008 | Створення та модифікація дегустаційного відгуку | AC-008.1, AC-008.2, AC-008.3 | BDD-SCN-003 | [TODO] | [TODO] | Specified |
| REQ-F-009 | Автоматичне формування Колекції та автоочищення Wishlist | AC-009.1, AC-009.2 | BDD-SCN-001 | [TODO] | [TODO] | Specified |
| REQ-F-010 | М'яке видалення продукту модератором та Placeholder Beer | AC-010.1, AC-010.2 | N/A | [TODO] | [TODO] | Specified |
| REQ-F-011 | Злиття дублікатів модератором (Merge) | AC-011.1, AC-011.2, AC-011.3 | N/A | [TODO] | [TODO] | Specified |
| REQ-F-012 | Розрахунок аналітики споживання, часових поясів та стріків | AC-012.1, AC-012.2, AC-012.3 | N/A | [TODO] | [TODO] | Specified |
| REQ-NF-001 | Швидкодія операцій читання каталогу (p95 <= 100 мс при 100 RPS) | AC-NF-001.1 | N/A | [TODO] | [TODO] | Specified |
| REQ-NF-002 | Швидкодія формування аналітики та стріків (p95 <= 250 мс при 30 RPS) | AC-NF-002.1 | N/A | [TODO] | [TODO] | Specified |
| REQ-NF-003 | Пропускна здатність запису споживання (50 Write RPS, p95 <= 150 мс) | AC-NF-003.1 | N/A | [TODO] | [TODO] | Specified |
| REQ-NF-004 | Автентифікація через stateless JWT та ротація Refresh Token | AC-NF-004.1 | N/A | [TODO] | [TODO] | Specified |
| REQ-NF-005 | Ізоляція приватних логів споживання (403/404 при доступі до чужих даних) | AC-NF-005.1 | N/A | [TODO] | [TODO] | Specified |
| REQ-NF-006 | Криптографічне солене хешування паролів (Argon2id / BCrypt) | AC-NF-006.1 | N/A | [TODO] | [TODO] | Specified |
| REQ-NF-007 | Транзакційна цілісність ACID (рівень Read Committed, Rollback) | AC-NF-007.1 | N/A | [TODO] | [TODO] | Specified |
| REQ-NF-008 | Стандартизація помилок API за специфікацією RFC 7807 | AC-NF-008.1 | BDD-SCN-004 | [TODO] | [TODO] | Specified |
| REQ-NF-009 | Запобігання гонкам даних (Race Conditions) при голосуванні за версії | AC-NF-009.1 | N/A | [TODO] | [TODO] | Specified |
