# Scope Boundaries and Domain Glossary

**Document Version:** 1.1  
**Project:** Beer Tracker & Personal Tasting Catalog  
**Status:** Approved (SPEC-GATE Audit Compliant)  
**Date:** September 20, 2026  

---

## 1. Scope Boundaries

### 1.1. In-Scope (Committed Capabilities)

| ID | Functional Area | Committed Feature Scope |
| :--- | :--- | :--- |
| **IN-01** | **User Identity & Sybil Defense** | Email registration, JWT authentication, IP-based registration rate-limiting (max 5 accounts/IP/hour), CAPTCHA. Account maturity verification (age $\ge 48\text{ hours}$ AND $\ge 1$ committed `ConsumptionEvent`) required for catalog editing and consensus voting. Daily creation quota (max 5 new products/user/24 hours). |
| **IN-02** | **Crowdsourced Catalog** | Wiki-style collaborative product catalog. Dual-version concurrency: a product record supports exactly one `VerifiedVersion` and at most one pending `DraftVersion`. User toggle to switch between "Latest Draft" (default) and "Verified Only". Creator identity preserved strictly in internal audit tables and redacted from public API schemas. |
| **IN-03** | **Consensus Governance** | Community verification engine: 10 `UPVOTE` votes elevate a `DraftVersion` to `VerifiedVersion`; 5 `DOWNVOTE` votes reject it (new submissions transition to `Untrusted`, edits are discarded). Race conditions resolved via pessimistic row locking (First-Commit Wins). Authors cannot vote on their own submissions; maximum 1 vote per user per version. |
| **IN-04** | **Product Deduplication** | Prevention of duplicate products via unique barcode index (EAN-13, UPC) and normalized composite key: `lowercase(trim(name)) + lowercase(trim(brewery)) + serving_type`. |
| **IN-05** | **Packaging & Serving Types** | Multi-package variants for packaged beer (`ServingType.PACKAGED`) supporting multiple package volumes and barcodes under a single product entity. Draft (on-tap) beer (`ServingType.DRAFT`) is modeled as an independent product entity. |
| **IN-06** | **Quantitative Consumption Tracking** | Append-only ledger recording `ConsumptionEvent` (`event_id`, `user_id`, `product_id`, `volume_liters`, `timestamp_utc`). Strict server-side immutability (no SQL `UPDATE` or `DELETE` for standard application connection pools). |
| **IN-07** | **Soft-Undo & Offline Sync** | 60-second client-side countdown buffer for online consumption entries. Offline queue persisted locally in non-volatile storage (IndexedDB/SQLite); upon network reconnection, queued events sync immediately to the server with their original UTC creation timestamps. |
| **IN-08** | **Collection & Wishlist** | Personal `Collection` automatically formed upon first committed consumption. Automatic removal from `Wishlist` upon transition to `Collection`. Manual addition and removal for `Wishlist`. Soft-Undo cancellation within 60 seconds cancels `Collection` addition. |
| **IN-09** | **Qualitative Tasting Diary** | Single `Review` per product per user, strictly restricted to products in personal `Collection`. `Rating` scale from 1.0 to 5.0 with 0.5 step. Optional comment up to 2000 characters. Author can edit or delete their review at any time. Public catalog displays arithmetic mean rating, review count, and personal rating. |
| **IN-10** | **Localized Temporal Analytics** | Volume totals (Liters) for local day, week, and month. Conversion of UTC timestamps to client-specified IANA timezone for calendar boundaries (00:00:00 to 23:59:59). Strict validation of IANA timezone with HTTP 400 on error. `CurrentStreak` and `LongestStreak` calculation. 24-hour histogram profile. |
| **IN-11** | **Frequency-Based Rankings** | Top rankings for beers, breweries, and styles ordered strictly by consumption frequency and volume, ignoring star scores. Secondary tie-breaking ordered strictly by latest consumption date (`last_consumed_at DESC`). |
| **IN-12** | **Administrative Purge** | Administrator privilege to delete fraudulent products. To preserve referential integrity without violating consumption immutability, a privileged stored procedure under `SECURITY DEFINER` reassigns foreign keys to a system `Tombstone` placeholder (`[Deleted / Unknown Product]`). |

---

### 1.2. Out-of-Scope (Strictly Excluded / Deferred)

| ID | Excluded Capability | Rationale & Architectural Rule |
| :--- | :--- | :--- |
| **OUT-01** | **External Database Integration** | No external APIs (Untappd, RateBeer, Open Food Facts). The catalog is 100% self-contained and crowdsourced. |
| **OUT-02** | **Ethanol & Biometrics** | No computation of pure alcohol grams ($C_2H_5OH$), Blood Alcohol Concentration (BAC), calories, or health platform sync. |
| **OUT-03** | **Social Networking** | No public user profiles, friend lists, activity feeds, direct messages, following, or commenting on other users' reviews. |
| **OUT-04** | **Commercial & Retail Operations** | No price tracking, venue tap-lists, bottle shop inventories, GPS bar finders, or receipt OCR scanning. |
| **OUT-05** | **Retroactive Server-Side Editing** | No API endpoints allowing standard users to modify or delete historical `ConsumptionEvent` records once committed. |
| **OUT-06** | **Complex Sensory Profiling** | No radar/spider sensory charts, BJCP flavor wheels, SRM color spectrum analyzers, or structured flavor tags. |
| **OUT-07** | **Gamification Subsystems** | No badges, XP, levels, or competitive inter-user leaderboards. |

---

## 2. Domain Glossary (Ubiquitous Language)

| Term (Canonical) | Definition | Allowed Synonyms | Forbidden Synonyms *(DO NOT USE)* | Usage Context |
| :--- | :--- | :--- | :--- | :--- |
| **`BeerProduct`** | A distinct beer formulation defined by its name, brewery, style, ABV, and serving category. | `Product` | *Item*, *Drink*, *Beverage*, *BeerEntry* | Domain Model, DB Table (`beer_products`), REST API (`/api/v1/products`) |
| **`ServingType`** | Classification of how the beer is packaged and served: pre-packaged (`PACKAGED`) or on-tap (`DRAFT`). | `PackagingCategory` | *Type*, *Container*, *Kind* | Domain Enum (`ServingType.PACKAGED`, `ServingType.DRAFT`), DB Column |
| **`PackageVariant`** | A physical packaging manifestation of a `PACKAGED` beer, defined by volume (ml/L) and an associated barcode. | `ProductPackage` | *Bottle*, *Can*, *SKU*, *Unit* | Domain Model, DB Table (`product_package_variants`) |
| **`ConsumptionEvent`** | A single immutable record of a user drinking a specific volume of a `BeerProduct` at a specific point in time. | `ConsumptionLog`, `Consumption` | *DrinkLog*, *CheckIn*, *Intake*, *Transaction* | Domain Model, DB Table (`consumption_events`), REST API (`/api/v1/consumption`) |
| **`SoftUndo`** | A client-side mechanism that buffers a newly logged online `ConsumptionEvent` for 60 seconds before committing it to the server. | `UndoBuffer`, `PendingSubmission` | *Rollback*, *DeleteConsumption*, *CancelRequest* | Client Service, UI Component (`SoftUndoBanner`) |
| **`OfflineQueue`** | Persistent local client storage (IndexedDB/SQLite) retaining uncommitted `ConsumptionEvent` records created while disconnected. | `ClientEventQueue` | *OfflineCache*, *DraftEvents*, *SyncBuffer* | Client Architecture, Sync Manager |
| **`Collection`** | The personal catalog of unique `BeerProduct` records that an individual user has consumed at least once in the system. | `TastedList`, `PersonalCollection` | *Library*, *Inventory*, *BeerCellar*, *History* | Domain Entity, DB Table (`user_collections`), UI View |
| **`Wishlist`** | A user-curated set of `BeerProduct` records that the user intends to taste in the future. | `ToTasteList` | *Favorites*, *Bookmarks*, *Watchlist*, *Cart* | Domain Entity, DB Table (`user_wishlists`), UI View |
| **`Review`** | A user's qualitative evaluation of a `BeerProduct` in their `Collection`, comprising a numerical rating and an optional text comment. | `TastingReview` | *Feedback*, *Comment*, *Critique*, *Post* | Domain Model, DB Table (`product_reviews`), REST API (`/api/v1/reviews`) |
| **`Rating`** | The numerical component of a `Review`, strictly constrained to the range 1.0 to 5.0 with step 0.5. | `Score`, `RatingScore` | *Stars*, *Grade*, *Mark*, *Points* | Domain Value Object, DTO Field |
| **`VerifiedVersion`** | The authoritative, community-approved version of a `BeerProduct` record that has reached the consensus threshold. | `VerifiedRecord` | *ApprovedVersion*, *FinalVersion*, *MasterRecord* | Domain Entity, DB Column (`verification_status = 'VERIFIED'`) |
| **`DraftVersion`** | A newly submitted or edited version of a `BeerProduct` awaiting verification through community consensus. | `PendingDraft`, `UnverifiedVersion` | *TempProduct*, *ProposedVersion*, *WorkingCopy* | Domain Entity, DB Column (`verification_status = 'DRAFT'`) |
| **`Untrusted`** | Lifecycle status of a new `BeerProduct` that received $\ge 5$ rejection votes; hidden from global discovery but preserved for existing loggers. | `QuarantinedProduct` | *Spam*, *Blacklisted*, *RejectedProduct*, *SoftDeleted* | Domain Status Enum (`VerificationStatus.UNTRUSTED`) |
| **`ConsensusVote`** | A binary evaluation (`UPVOTE` or `DOWNVOTE`) cast by an eligible user on a `DraftVersion`. | `VerificationVote` | *Like*, *Dislike*, *Approval*, *ReviewVote* | Domain Model, DB Table (`consensus_votes`), REST API |
| **`Tombstone`** | An immutable system placeholder entity (`[Deleted / Unknown Product]`) that replaces products purged by an Administrator. | `PlaceholderProduct` | *NullObject*, *GhostProduct*, *DummyBeer* | Domain Entity, Seed Data (`product_id = 0` or UUID nil) |
| **`DrinkingDay`** | A calendar day (00:00:00 to 23:59:59 in the user's local IANA timezone) containing at least one committed `ConsumptionEvent`. | `ActiveDay` | *AlcoholDay*, *BeerDay*, *Session* | Analytics Domain, DTO (`DrinkingDayDTO`) |
| **`Streak`** | A consecutive series of `DrinkingDay` intervals without intermediate interruption. Comprises `CurrentStreak` and `LongestStreak`. | `DrinkingStreak` | *Habit*, *Chain*, *Run*, *Series* | Analytics Domain, DTO (`StreakSummaryDTO`) |
| **`AccountMaturity`** | Security gate state requiring account age $\ge 48$ hours and $\ge 1$ committed consumption before granting catalog editing/voting privileges. | `UserMaturityStatus` | *TrustScore*, *Karma*, *UserRank*, *Level* | Security Filter, User Domain (`isMatured()`) |
| **`ABV`** | Alcohol by Volume: standard international metric expressing ethanol percentage in total liquid volume at 20°C. | `AlcoholPercentage` | *Proof*, *Strength*, *AlcoholContent* | Domain Value Object, DTO Field |
| **`CAPTCHA`** | Completely Automated Public Turing test to tell Computers and Humans Apart: automated challenge-response verification mechanism. | `AntiBotChallenge` | *BotFilter*, *HumanTest* | Security Filter, Auth API |
| **`EAN-13 / UPC`** | International Article Number (13 digits) and Universal Product Code (12 digits): global standard optical barcode symbologies. | `Barcode` | *Code*, *ProductCode*, *Serial* | Domain Field, DTO Field |
