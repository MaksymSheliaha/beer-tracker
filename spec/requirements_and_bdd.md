# Formal Software Requirements and BDD Scenarios

**Document Version:** 1.1  
**Associated SRS:** [`spec/srs.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/srs.md)  
**Associated Scope & Glossary:** [`spec/scope_and_glossary.md`](file:///D:/uni/4-year/ПІС/beer-tracker/spec/scope_and_glossary.md)  
**Status:** Approved (SPEC-GATE Audit Compliant)  
**Date:** September 20, 2026  

---

## 1. Functional Requirements (REQ-F-xxx)

### REQ-F-001: New Product Registration with Anti-Abuse Gate
* **Description:** The system shall allow an authenticated user to submit a new beer product to the global catalog only if the user meets the account maturity threshold and has not exceeded their daily submission quota.
* **Acceptance Criteria:**
  * **AC-001.1:** If an account is younger than 48 hours OR has 0 committed consumption events, the submission is rejected with HTTP 403 Forbidden stating that the account maturity requirement is not satisfied.
  * **AC-001.2:** If an account has already submitted 5 new products within the trailing 24 hours, the submission is rejected with HTTP 429 Too Many Requests.
  * **AC-001.3:** Upon successful validation, the system shall save the product with status `DRAFT` and record the submitting user ID exclusively in the internal audit ledger.

---

### REQ-F-002: Product Deduplication
* **Description:** The system shall reject the creation of a new product if an identical barcode or an identical normalized composite key already exists in the catalog.
* **Acceptance Criteria:**
  * **AC-002.1:** If a provided barcode (EAN-13 or UPC) matches any barcode currently registered in the database, the system shall reject the creation with HTTP 409 Conflict and return a reference to the existing product.
  * **AC-002.2:** If the combination of `lowercase(trim(name)) + lowercase(trim(brewery)) + serving_type` matches an existing product, the system shall reject the creation with HTTP 409 Conflict.

---

### REQ-F-003: Crowdsourced Consensus Elevation and Rejection
* **Description:** The system shall track community verification votes on draft versions and automatically transition their status upon reaching voting thresholds, resolving race conditions via transactional row locking (First-Commit Wins).
* **Acceptance Criteria:**
  * **AC-003.1:** When a `DRAFT` version receives its 10th `UPVOTE`, the system shall atomically promote it to `VERIFIED`.
  * **AC-003.2:** If a user attempts to vote on a draft they authored, the vote shall be rejected with HTTP 400 Bad Request.
  * **AC-003.3:** If a user attempts to vote more than once on the same draft version, the subsequent vote shall be rejected with HTTP 409 Conflict.
  * **AC-003.4:** If a newly created draft receives 5 `DOWNVOTE`s, the system shall transition it to `UNTRUSTED` (hidden from catalog discovery). If an edited draft of an existing product receives 5 `DOWNVOTE`s, the draft shall be deleted/discarded and the prior `VERIFIED` version shall remain active.
  * **AC-003.5:** Concurrent voting requests shall execute under pessimistic row locking (`SELECT FOR UPDATE`). The transaction that first reaches the threshold (10 Upvotes or 5 Downvotes) executes the state transition; any subsequent concurrent vote on the closed voting round is rejected with HTTP 409 Conflict.

---

### REQ-F-004: Dual-Version Catalog Presentation
* **Description:** The system shall provide a catalog browsing interface that displays either the latest draft or only verified versions based on the user's view preference.
* **Acceptance Criteria:**
  * **AC-004.1:** By default, the catalog shall display the latest available version (`DRAFT` if present, otherwise `VERIFIED`) with a visual badge indicating verification status.
  * **AC-004.2:** When the user enables the "Verified Only" toggle, all unverified `DRAFT` entries and edits shall be filtered out, displaying strictly `VERIFIED` records.

---

### REQ-F-005: Creator Anonymity and Non-Disclosure
* **Description:** The system shall redact and prevent disclosure of the product author's identity in public interfaces and API endpoints.
* **Acceptance Criteria:**
  * **AC-005.1:** All catalog product retrieval API responses (single product, search results, lists) shall not contain `created_by`, `user_id`, or author metadata fields.
  * **AC-005.2:** The author identity shall only be accessible to users with the `ADMINISTRATOR` role via dedicated audit endpoints.

---

### REQ-F-006: Quantitative Consumption Logging with Soft-Undo
* **Description:** The system shall provide a 60-second client-side buffer allowing the user to cancel an entered online consumption log before it is transmitted and committed to the server.
* **Acceptance Criteria:**
  * **AC-006.1:** When a user logs a consumption event while online, a 60-second countdown timer shall be initiated on the client.
  * **AC-006.2:** If the user clicks "Undo" before the timer expires, the event is discarded locally and never transmitted to the server.
  * **AC-006.3:** When the 60-second timer elapses, the client automatically dispatches the event to the server, where it is persisted to the append-only ledger with its original UTC creation timestamp. Once received by the server, the event cannot be deleted or modified.

---

### REQ-F-007: Offline Consumption Buffering and Immediate Reconnection Sync
* **Description:** The system shall allow users to record consumption events without network connectivity and immediately synchronize them once connectivity is restored.
* **Acceptance Criteria:**
  * **AC-007.1:** When offline, logged events are saved to local persistent storage (IndexedDB/SQLite), and the user may cancel any queued event locally at any time while offline.
  * **AC-007.2:** When network connectivity is restored, the client shall immediately transmit all queued offline events to the server in FIFO sequence without an additional 60-second delay, preserving each event's original UTC creation timestamp.

---

### REQ-F-008: Automatic Collection Formation and Wishlist Transition
* **Description:** The system shall maintain personal Collections and Wishlists based on committed consumption events.
* **Acceptance Criteria:**
  * **AC-008.1:** When the first consumption event for a product is committed, the product shall be added to the user's `Collection`.
  * **AC-008.2:** If the product was in the user's `Wishlist`, it shall be automatically removed from the `Wishlist` upon being added to the `Collection`.
  * **AC-008.3:** If an online event is canceled via Soft-Undo within the 60-second window, the product shall not be added to the `Collection`, and any pending removal from the `Wishlist` shall be aborted.

---

### REQ-F-009: Product Rating and Reviewing Restricted to Collection
* **Description:** The system shall permit users to rate and review only products present in their personal Collection, enforcing bounds on scores and text comments.
* **Acceptance Criteria:**
  * **AC-009.1:** If a user attempts to submit a review for a product not in their `Collection`, the operation shall be rejected with HTTP 403 Forbidden.
  * **AC-009.2:** The rating value must be between 1.0 and 5.0 with a step of 0.5; any other numerical value shall be rejected with HTTP 400 Bad Request.
  * **AC-009.3:** A user may update or delete their existing review at any time, which immediately recalculates the product's global average rating and review count.
  * **AC-009.4:** The review text comment is optional. If provided, it must not exceed 2000 UTF-8 characters; payloads exceeding 2000 characters shall be rejected with HTTP 400 Bad Request.

---

### REQ-F-010: Localized Temporal Analytics and Streak Calculation
* **Description:** The system shall compute consumption metrics and drinking streaks adjusted to the client's local IANA timezone, rejecting invalid timezone identifiers.
* **Acceptance Criteria:**
  * **AC-010.1:** The server shall convert stored UTC timestamps into the user's local timezone (00:00:00 to 23:59:59) to determine `DrinkingDay` calendar boundaries.
  * **AC-010.2:** The system shall return total consumed volume in Liters grouped by current local day, current local week (Monday–Sunday), and current local month.
  * **AC-010.3:** The system shall accurately output `CurrentStreak` (consecutive active days leading up to today or yesterday) and `LongestStreak`.
  * **AC-010.4:** If the request supplies an unparseable or unrecognized IANA timezone identifier, the server shall return HTTP 400 Bad Request with an invalid timezone error message.

---

### REQ-F-011: Frequency-Based Qualitative Leaderboards and Tie-Breaking
* **Description:** The system shall generate top rankings for most consumed beers, breweries, and styles based strictly on consumption event frequency and volume, with deterministic tie-breaking.
* **Acceptance Criteria:**
  * **AC-011.1:** Ranking position shall be determined by the total count of consumption occasions (and total volume in Liters), strictly ignoring user rating scores.
  * **AC-011.2:** Products or breweries with higher rating scores but fewer consumption occasions shall rank lower than products with more consumption occasions.
  * **AC-011.3:** In the event of an exact tie in both consumption count and volume, the system shall order entries strictly by latest consumption date (`last_consumed_at DESC`).

---

### REQ-F-012: Administrative Purge with Privileged Tombstone Substitution
* **Description:** The system shall permit administrators to purge fraudulent products while preserving referential integrity and historical consumption volumes via a privileged stored procedure.
* **Acceptance Criteria:**
  * **AC-012.1:** When an Administrator invokes product deletion, a database stored procedure executing with `SECURITY DEFINER` privileges shall atomically update all `product_id` foreign keys in `consumption_events` to the system `Tombstone` ID (`[Deleted / Unknown Product]`).
  * **AC-012.2:** Following the purge, historical volume calculations and streak counts for users who logged that product remain intact and unchanged.

---

### REQ-F-013: Product Draft Submission for Existing Product Edits
* **Description:** The system shall permit a mature user to submit proposed revisions to an existing verified product, creating a pending draft version without overwriting the active verified version.
* **Acceptance Criteria:**
  * **AC-013.1:** Only authenticated users satisfying the `AccountMaturity` requirement can submit edits for an existing product. Immature users receive HTTP 403 Forbidden.
  * **AC-013.2:** If the product already has a pending `DraftVersion`, submitting a new edit replaces the prior draft and resets all consensus vote counters (`upvotes = 0`, `downvotes = 0`).
  * **AC-013.3:** The existing `VerifiedVersion` remains active and served to users until the new draft successfully reaches 10 `UPVOTE` votes.

---

### REQ-F-014: Manual Wishlist Management
* **Description:** The system shall permit authenticated users to explicitly add existing catalog products to their personal Wishlist and remove them without logging a consumption event.
* **Acceptance Criteria:**
  * **AC-014.1:** An authenticated user may add any existing catalog product to their `Wishlist` via `POST /api/v1/users/me/wishlist`. Submitting a product already present in the wishlist is idempotent and returns HTTP 200 OK.
  * **AC-014.2:** An authenticated user may remove a product from their `Wishlist` via `DELETE /api/v1/users/me/wishlist/{product_id}`. If the product is not in the wishlist, the request returns HTTP 404 Not Found.

---

## 2. Non-Functional Requirements (REQ-NF-xxx)

### 2.1 Performance Requirements

* **REQ-NF-001 (Catalog Read Latency and Throughput):**
  * The 95th percentile (p95) response time for public product search, catalog retrieval, and review queries shall be $\le 200\text{ ms}$ under a sustained load of 100 requests per second (RPS).
* **REQ-NF-002 (Consumption Event Ingestion Throughput):**
  * The server-side event ingestion endpoint (`POST /api/v1/consumption`) shall achieve a 99th percentile (p99) response time of $\le 100\text{ ms}$ under a sustained load of 50 write requests per second (RPS).
* **REQ-NF-003 (Analytics Query Processing):**
  * The 95th percentile (p95) computation time for complete user analytics (aggregates, streaks, calendar days, hourly distributions) shall be $\le 500\text{ ms}$ for user accounts containing up to 10,000 historical consumption events under a concurrency of 20 RPS.

---

### 2.2 Security Requirements

* **REQ-NF-004 (Credential Security):**
  * User passwords must be hashed using bcrypt with a work factor $\ge 12$ or Argon2id ($m=65536, t=3, p=4$) prior to database persistence.
* **REQ-NF-005 (Data Transmission and Access Control):**
  * 100% of external traffic must be encrypted via TLS 1.3 over HTTPS.
  * Standard users shall not be authorized to read or query another user's consumption events, wishlist, or collection (enforced via JWT claim validation and database tenant isolation).
* **REQ-NF-006 (Sybil Attack & Registration Throttling):**
  * The registration endpoint shall limit account creation to a maximum of 5 accounts per IP address per hour.
  * Accounts failing the maturity threshold (age $< 48\text{ hours}$ or $0$ confirmed consumptions) shall receive HTTP 403 Forbidden when calling product creation or voting endpoints.
* **REQ-NF-007 (Author Identity Redaction):**
  * Product serialization components and DTO mappers shall exclude the `created_by_user_id` attribute from all non-administrative API schemas, preventing author tracking via reverse engineering.

---

### 2.3 Reliability, Offline Sync, and Data Integrity

* **REQ-NF-008 (Zero Data Loss in Offline Storage):**
  * Client-side persistent storage (IndexedDB on Web, SQLite on Mobile) shall retain uncommitted consumption events across unexpected application terminations, browser restarts, and device power cycles with zero loss of queued records.
* **REQ-NF-009 (Sync Idempotency):**
  * The consumption ingestion endpoint shall enforce idempotency using a client-generated UUID per event. Submitting the same UUID multiple times shall return HTTP 200/201 without creating duplicate ledger records.
* **REQ-NF-010 (Server-Side Immutability and Privileged Tombstone Procedure):**
  * Standard application database connection pools shall be granted strictly `INSERT` and `SELECT` privileges on the `consumption_events` table (denying `UPDATE` and `DELETE`).
  * Tombstone foreign key replacement during administrative purge shall execute strictly via a dedicated stored procedure granted `SECURITY DEFINER` permissions, ensuring standard application sessions cannot modify consumption records.

---

## 3. BDD Scenarios (Behavior-Driven Development)

### BDD-SCN-001: First-Time Consumption with Soft-Undo Expiration (Happy Path)
```gherkin
Feature: Logging Beer Consumption
  As an authenticated user
  I want to log a consumed beer
  So that it is recorded in my personal collection and analytics

  Scenario: BDD-SCN-001 - First-time consumption commits after 60s soft-undo
    Given the user "Oleg" is authenticated and online
    And the beer "Pilsner Urquell" is present in Oleg's Wishlist
    And "Pilsner Urquell" is not present in Oleg's Collection
    When Oleg logs the consumption of 0.5 Liters of "Pilsner Urquell" at "2026-09-20T17:00:00Z"
    Then the client displays a 60-second Soft-Undo countdown banner
    And no consumption record is sent to the server yet
    When 60 seconds elapse without Oleg clicking "Undo"
    Then the client dispatches the consumption event to the server
    And the server commits the 0.5L event to the immutable consumption log
    And "Pilsner Urquell" is automatically added to Oleg's Collection
    And "Pilsner Urquell" is automatically removed from Oleg's Wishlist
    And Oleg's current drinking streak is recalculated
```

---

### BDD-SCN-002: Crowdsourced Draft Elevation via 10th Consensus Vote (Happy Path)
```gherkin
Feature: Catalog Consensus Elevation
  As the system
  I want to promote drafts to verified status upon consensus
  So that the community maintains an accurate verified catalog

  Scenario: BDD-SCN-002 - 10th verification vote elevates a draft product
    Given a product draft "Kyiv Porter" has status "DRAFT"
    And "Kyiv Porter" has accumulated exactly 9 Upvotes
    And the user "Max" is authenticated, has account age of 72 hours, and has 3 logged consumptions
    And "Max" is not the author of "Kyiv Porter"
    And "Max" has not previously voted on "Kyiv Porter"
    When "Max" submits an "UPVOTE" for "Kyiv Porter"
    Then the server records the vote for "Max"
    And the total Upvote count for "Kyiv Porter" becomes 10
    And the status of "Kyiv Porter" atomically changes from "DRAFT" to "VERIFIED"
    And "Kyiv Porter" becomes visible in queries using the "Verified Only" filter
```

---

### BDD-SCN-003: Soft-Undo Cancellation within 60 Seconds (Edge Case / Cancellation)
```gherkin
Feature: Soft-Undo Cancellation
  As an authenticated user
  I want to cancel an accidental consumption entry
  So that my consumption logs and collection remain accurate

  Scenario: BDD-SCN-003 - User cancels entry within the 60-second soft-undo window
    Given the user "Iryna" is authenticated and online
    And the beer "Dark Dunkel" is not in Iryna's Collection
    When Iryna accidentally taps to log 1.0 Liter of "Dark Dunkel"
    And the client starts the 60-second Soft-Undo countdown timer
    And Iryna clicks the "Undo" button after 15 seconds
    Then the client immediately aborts the timer and discards the pending event
    And no network request is transmitted to the server
    And "Dark Dunkel" is not added to Iryna's Collection
    And Iryna's analytics and consumption totals remain unchanged
```

---

### BDD-SCN-004: Duplicate Product Submission with Matching Barcode (Edge Case / Validation Error)
```gherkin
Feature: Catalog Deduplication
  As the catalog service
  I want to reject duplicate product creations
  So that the global catalog avoids fragmenting product reviews and barcodes

  Scenario: BDD-SCN-004 - User submits a product with an existing barcode
    Given an existing product "Lvivske 1715" is registered with barcode "4820000123456"
    And the user "Taras" meets the account maturity threshold
    When "Taras" attempts to create a new product named "Lviv 1715 Special" with barcode "4820000123456"
    Then the server rejects the request with HTTP 409 Conflict
    And the response payload specifies "Duplicate barcode: 4820000123456 already linked to product 'Lvivske 1715'"
    And no new product or draft is created in the database
```

---

### BDD-SCN-005: Immature Account Attempting to Vote in Consensus (Edge Case / Security Violation)
```gherkin
Feature: Anti-Abuse and Sybil Protection
  As the security subsystem
  I want to restrict consensus voting to mature accounts
  So that malicious actors cannot flood the voting system with puppet accounts

  Scenario: BDD-SCN-005 - User with brand-new account attempts to vote
    Given a new user "BotNet_42" registered 2 hours ago
    And "BotNet_42" has 0 committed consumption events
    And a product draft "Fake Lager" is pending consensus voting
    When "BotNet_42" attempts to cast an "UPVOTE" on "Fake Lager"
    Then the server rejects the request with HTTP 403 Forbidden
    And the response error states "Account maturity requirement not met: minimum age of 48 hours and at least 1 logged consumption required"
    And the vote count on "Fake Lager" remains unchanged
```

---

### BDD-SCN-006: Rejection of Invalid IANA Timezone (Edge Case / Validation Error)
```gherkin
Feature: Temporal Analytics Timezone Validation
  As an API consumer
  I want clear validation errors when sending invalid timezones
  So that analytics calculations do not produce corrupted calendar buckets

  Scenario: BDD-SCN-006 - User requests analytics with an invalid timezone
    Given the user "Oleg" is authenticated
    When Oleg requests consumption analytics with timezone query parameter "Mars/Olympus_Mons"
    Then the server responds with HTTP 400 Bad Request
    And the response body contains "Invalid IANA timezone identifier: Mars/Olympus_Mons"
```
