#!/usr/bin/env bash
# ==============================================================================
# Beer Tracker Backend — OpenAPI Contract & Mock Scenarios Verification Suite
# Target: Stoplight Prism Mock Server (http://localhost:4010)
# Contract: api/openapi.yaml (OpenAPI 3.0.3)
# ==============================================================================

set -euo pipefail

export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

BASE_URL="${BASE_URL:-http://localhost:4010}"
AUTH_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMGE4MDEyMy00MjAxLTQ0NDQtOTk5OS0wMTIzNDU2Nzg5YWIiLCJyb2xlIjoiVVNFUiJ9.mock_sig"
AUTH_HEADER="Authorization: Bearer ${AUTH_TOKEN}"

if command -v python >/dev/null 2>&1; then
  PYTHON_BIN="python"
elif command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN="python3"
else
  echo "[FATAL] Python interpreter not found for JSON contract validation." >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
HDR_FILE="${TMP_DIR}/headers.txt"
BODY_FILE="${TMP_DIR}/body.json"
trap 'rm -rf "${TMP_DIR}"' EXIT

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

print_banner() {
  echo "=============================================================================="
  echo " Beer Tracker API — Contract & ErrorResponse Verification (Prism Mock)"
  echo " Target Base URL: ${BASE_URL}"
  echo "=============================================================================="
}

run_python_dto_check() {
  local schema_name="$1"
  local required_fields="$2"
  "${PYTHON_BIN}" -c '
import json, sys

schema_name = sys.argv[1]
req_fields = [f.strip() for f in sys.argv[2].split(",") if f.strip()]
raw = sys.stdin.read()
data = json.loads(raw)

if not isinstance(data, dict):
    raise SystemExit(f"Expected JSON object for {schema_name}, got {type(data).__name__}")

missing = [f for f in req_fields if f not in data]
if missing:
    raise SystemExit(f"Missing required fields in {schema_name}: {missing}")

def fmt_val(v):
    if isinstance(v, list):
        return f"[{len(v)} item(s)]"
    return repr(v)

summary_keys = req_fields[:4]
summary = ", ".join(f"{k}={fmt_val(data[k])}" for k in summary_keys)
print(f"  Contract    : {schema_name} valid ({len(req_fields)} required fields verified)")
print(f"  Payload     : {summary}")
' "${schema_name}" "${required_fields}" < "${BODY_FILE}"
}

assert_positive_json() {
  local scenario_id="$1"
  local description="$2"
  local expected_status="$3"
  local actual_status="$4"
  local schema_name="$5"
  local required_fields="$6"

  TOTAL_TESTS=$((TOTAL_TESTS + 1))
  echo ""
  echo "------------------------------------------------------------------------------"
  echo "[${scenario_id}] ${description}"
  echo "  HTTP Status : expected=${expected_status}, actual=${actual_status}"

  if [[ "${actual_status}" != "${expected_status}" ]]; then
    echo "  Result      : [FAIL] Status code mismatch!"
    cat "${BODY_FILE}" || true
    FAILED_TESTS=$((FAILED_TESTS + 1))
    return 1
  fi

  if run_python_dto_check "${schema_name}" "${required_fields}"; then
    echo "  Result      : [PASS]"
    PASSED_TESTS=$((PASSED_TESTS + 1))
  else
    echo "  Result      : [FAIL] Response payload violated ${schema_name} contract!"
    cat "${BODY_FILE}" || true
    FAILED_TESTS=$((FAILED_TESTS + 1))
    return 1
  fi
}

assert_no_content() {
  local scenario_id="$1"
  local description="$2"
  local expected_status="$3"
  local actual_status="$4"

  TOTAL_TESTS=$((TOTAL_TESTS + 1))
  echo ""
  echo "------------------------------------------------------------------------------"
  echo "[${scenario_id}] ${description}"
  echo "  HTTP Status : expected=${expected_status}, actual=${actual_status}"

  if [[ "${actual_status}" != "${expected_status}" ]]; then
    echo "  Result      : [FAIL] Status code mismatch!"
    cat "${BODY_FILE}" || true
    FAILED_TESTS=$((FAILED_TESTS + 1))
    return 1
  fi

  local body_size
  body_size=$(wc -c < "${BODY_FILE}" | tr -d '[:space:]')
  if [[ "${body_size}" != "0" ]]; then
    echo "  Result      : [FAIL] Expected empty body for 204 No Content, got ${body_size} bytes"
    FAILED_TESTS=$((FAILED_TESTS + 1))
    return 1
  fi

  echo "  Contract    : Empty payload verified (0 bytes)"
  echo "  Result      : [PASS]"
  PASSED_TESTS=$((PASSED_TESTS + 1))
}

assert_error_contract() {
  local scenario_id="$1"
  local description="$2"
  local expected_status="$3"
  local actual_status="$4"
  local expect_dynamic_violation="$5"

  TOTAL_TESTS=$((TOTAL_TESTS + 1))
  echo ""
  echo "------------------------------------------------------------------------------"
  echo "[${scenario_id}] ${description}"
  echo "  HTTP Status : expected=${expected_status}, actual=${actual_status}"

  if [[ "${actual_status}" != "${expected_status}" ]]; then
    echo "  Result      : [FAIL] Status code mismatch!"
    cat "${BODY_FILE}" || true
    FAILED_TESTS=$((FAILED_TESTS + 1))
    return 1
  fi

  local violations_line=""
  violations_line=$(grep -i "^sl-violations:" "${HDR_FILE}" | tr -d '\r' || true)

  if [[ "${expect_dynamic_violation}" == "true" ]]; then
    if [[ -z "${violations_line}" ]]; then
      echo "  Result      : [FAIL] Expected Prism dynamic validation header (sl-violations), but none was returned!"
      FAILED_TESTS=$((FAILED_TESTS + 1))
      return 1
    fi
    echo "  Prism Check : Dynamic validation triggered (${violations_line})"
  else
    echo "  Prism Check : Deterministic simulation via Prefer header"
  fi

  if "${PYTHON_BIN}" -c '
import datetime, json, sys, uuid

raw = sys.stdin.read()
data = json.loads(raw)

required = ["code", "message", "details", "timestamp", "traceId"]
missing = [k for k in required if k not in data]
if missing:
    raise SystemExit(f"ErrorResponse missing required fields: {missing}")

if not isinstance(data["code"], str) or not data["code"].strip():
    raise SystemExit("ErrorResponse.code must be a non-empty string")
if not isinstance(data["message"], str) or not data["message"].strip():
    raise SystemExit("ErrorResponse.message must be a non-empty string")
if not isinstance(data["details"], list):
    raise SystemExit("ErrorResponse.details must be a JSON array")

for idx, item in enumerate(data["details"]):
    if not isinstance(item, dict) or "field" not in item or "issue" not in item:
        raise SystemExit(f"ErrorResponse.details[{idx}] must contain field and issue")
    if not isinstance(item["field"], str) or not isinstance(item["issue"], str):
        raise SystemExit(f"ErrorResponse.details[{idx}] field/issue must be strings")

ts = data["timestamp"].replace("Z", "+00:00")
datetime.datetime.fromisoformat(ts)
uuid.UUID(data["traceId"])

code_val = data["code"]
trace_val = data["traceId"]
if data["details"]:
    d0 = data["details"][0]
    f_name = d0["field"]
    f_issue = d0["issue"]
    first_detail = f"{f_name}: {f_issue}"
else:
    first_detail = "[]"
print(f"  Contract    : ErrorResponse valid (code={code_val}, traceId={trace_val})")
print(f"  Detail[0]   : {first_detail}")
' < "${BODY_FILE}"; then
    echo "  Result      : [PASS]"
    PASSED_TESTS=$((PASSED_TESTS + 1))
  else
    echo "  Result      : [FAIL] Response did not match unified ErrorResponse schema!"
    cat "${BODY_FILE}" || true
    FAILED_TESTS=$((FAILED_TESTS + 1))
    return 1
  fi
}

print_banner

# ==============================================================================
# PART 1: POSITIVE SCENARIOS (201, 200 List, 200 Get-by-ID, 200 Update, 204 Delete)
# ==============================================================================
echo ""
echo ">>> PART 1: POSITIVE API SCENARIOS"

# 1. API-POS-001 (BDD-SCN-001): Create consumption entry (201 Created)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/consumption-entries" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "beer_id": "b0a80123-0001-4444-9999-0123456789ab",
    "packaging_id": "30a80123-0001-4444-9999-0123456789ab",
    "quantity": 2,
    "consumed_at": "2026-09-20T20:30:00Z"
  }')
assert_positive_json \
  "API-POS-001" \
  "POST /consumption-entries — Створення запису споживання (BDD-SCN-001, REQ-F-006)" \
  "201" "${STATUS}" \
  "ConsumptionEntryResponse" \
  "id,beer_id,packaging_id,quantity,total_volume_ml,consumed_at,created_at,wishlist_autoremoved,collection_added"

# 2. API-POS-002: List beers with filtering, sorting & pagination (200 OK)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X GET "${BASE_URL}/beers?query=Varvar&style=India%20Pale%20Ale&beer_type=PACKAGED&page=0&size=20&sort=rating_avg,desc" \
  -H "Accept: application/json")
assert_positive_json \
  "API-POS-002" \
  "GET /beers?... — Список пива з фільтрацією та пагінацією (REQ-F-001, REQ-NF-001)" \
  "200" "${STATUS}" \
  "BeerPagedResponse" \
  "items,page,size,total_elements,total_pages,has_next,has_previous"

# 3. API-POS-003: Get beer card by ID (200 OK)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X GET "${BASE_URL}/beers/b0a80123-0012-4444-9999-0123456789ab?verified_only=true" \
  -H "Accept: application/json")
assert_positive_json \
  "API-POS-003" \
  "GET /beers/{id}?verified_only=true — Отримання продукту за ID (REQ-F-001, REQ-F-003)" \
  "200" "${STATUS}" \
  "BeerResponse" \
  "id,name,brewery,style,abv,beer_type,status,packagings,created_at"

# 4. API-POS-004: Update existing tasting review via PUT (200 OK)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X PUT "${BASE_URL}/beers/b0a80123-0001-4444-9999-0123456789ab/reviews" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "rating": 5.0,
    "review_text": "Після повторної дегустації - оцінка максимальна! Чудовий баланс."
  }')
assert_positive_json \
  "API-POS-004" \
  "PUT /beers/{id}/reviews — Оновлення дегустаційного відгуку (REQ-F-008)" \
  "200" "${STATUS}" \
  "ReviewResponse" \
  "id,beer_id,user_id,rating,created_at,updated_at"

# 5. API-POS-005 (BDD-SCN-002): Community consensus vote & auto-verification (200 OK)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/beer-versions/40a80123-0002-4444-9999-0123456789ab/votes" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "Prefer: example=threshold_reached_verified" \
  -d '{
    "vote_type": "CONFIRM"
  }')
assert_positive_json \
  "API-POS-005" \
  "POST /beer-versions/{id}/votes — Голосування спільноти N=5 (BDD-SCN-002, REQ-F-005)" \
  "200" "${STATUS}" \
  "BeerVersionVoteResponse" \
  "version_id,confirm_votes,reject_votes,status,message"

# 6. API-POS-006: Remove item from user wishlist (204 No Content)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X DELETE "${BASE_URL}/wishlist-items/b0a80123-0001-4444-9999-0123456789ab" \
  -H "${AUTH_HEADER}")
assert_no_content \
  "API-POS-006" \
  "DELETE /wishlist-items/{beer_id} — Видалення ресурсу зі списку бажань (REQ-F-009)" \
  "204" "${STATUS}"

# 7. API-POS-007: Moderator soft-delete beer -> Placeholder Beer (204 No Content)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X DELETE "${BASE_URL}/beers/b0a80123-0012-4444-9999-0123456789ab" \
  -H "${AUTH_HEADER}")
assert_no_content \
  "API-POS-007" \
  "DELETE /beers/{id} — М'яке видалення продукту модератором / Placeholder (REQ-F-010)" \
  "204" "${STATUS}"

# 8. API-POS-008: Moderator merge duplicate beers (200 OK)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/merges" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "source_beer_id": "b0a80123-0299-4444-9999-0123456789ab",
    "target_beer_id": "b0a80123-0012-4444-9999-0123456789ab"
  }')
assert_positive_json \
  "API-POS-008" \
  "POST /merges — Адміністративне злиття дублікатів (REQ-F-011, REQ-NF-007)" \
  "200" "${STATUS}" \
  "BeerMergeResponse" \
  "target_beer_id,migrated_consumptions_count,migrated_packagings_count,archived_reviews_count,status"

# 9. API-POS-009: Personal consumption analytics, streaks & hourly profile (200 OK)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X GET "${BASE_URL}/analytics/consumption?date=2026-10-20" \
  -H "${AUTH_HEADER}" \
  -H "Accept: application/json")
assert_positive_json \
  "API-POS-009" \
  "GET /analytics/consumption?date=2026-10-20 — Аналітика доби та стріків (REQ-F-012, REQ-NF-002)" \
  "200" "${STATUS}" \
  "ConsumptionAnalyticsResponse" \
  "timezone,current_streak,longest_streak,total_volume_ml,total_entries,hourly_profile"

# 10. API-POS-010: Manual moderator verification of BeerVersion (200 OK)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/beer-versions/40a80123-0002-4444-9999-0123456789ab/verify" \
  -H "${AUTH_HEADER}" \
  -H "Accept: application/json")
assert_positive_json \
  "API-POS-010" \
  "POST /beer-versions/{id}/verify — Ручна верифікація версії модератором (REQ-F-013)" \
  "200" "${STATUS}" \
  "BeerVersionResponse" \
  "id,beer_id,author_id,payload,confirm_votes,reject_votes,status,created_at"

# 11. API-POS-011: Resolve moderation queue item by moderator (200 OK)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/moderation-items/b0a80123-0999-4444-9999-0123456789ab/decision" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "decision": "APPROVE",
    "comment": "Перевірено броварню, продукт дійсний"
  }')
assert_positive_json \
  "API-POS-011" \
  "POST /moderation-items/{id}/decision — Опрацювання черги модерації (REQ-F-014)" \
  "200" "${STATUS}" \
  "ModerationDecisionResponse" \
  "beer_id,decision,processed_at"

# 12. API-POS-012: Authenticate user and issue JWT pair (200 OK)
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SuperSecretPassword123!"
  }')
assert_positive_json \
  "API-POS-012" \
  "POST /auth/login — Автентифікація JWT Access + Refresh (REQ-NF-004, REQ-NF-006)" \
  "200" "${STATUS}" \
  "TokenResponse" \
  "access_token,refresh_token,token_type,expires_in"

# ==============================================================================
# PART 2: NEGATIVE SCENARIOS (400, 422, 404, 409, 401, 403, 405)
# ==============================================================================
echo ""
echo ">>> PART 2: NEGATIVE API SCENARIOS (Dynamic Prism Validation & Prefer Simulation)"

# 7. API-NEG-001: Invalid query parameter type -> Dynamic 400 Bad Request
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X GET "${BASE_URL}/beers?size=invalid_int" \
  -H "Accept: application/json")
assert_error_contract \
  "API-NEG-001" \
  "GET /beers?size=invalid_int — Невалідний тип параметра (Dynamic 400, REQ-NF-008)" \
  "400" "${STATUS}" \
  "true"

# 8. API-NEG-002a: Missing required field 'name' in request body -> Dynamic 422 Unprocessable Entity
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/beers" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "brewery": "Mova Brewing Co.",
    "style": "Oatmeal Stout",
    "abv": 5.8,
    "beer_type": "PACKAGED",
    "packagings": [
      {
        "volume_ml": 500,
        "package_type": "CAN"
      }
    ]
  }')
assert_error_contract \
  "API-NEG-002a" \
  "POST /beers (missing required 'name') — Пропуск обов'язкового поля (Dynamic 422, REQ-F-001)" \
  "422" "${STATUS}" \
  "true"

# 9. API-NEG-002b: Invalid field type & minimum constraint violation -> Dynamic 422 Unprocessable Entity
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/beers" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "name": "Varvar Golden Ale On Tap",
    "brewery": "Varvar",
    "style": "Golden Ale",
    "abv": "invalid_float_type",
    "beer_type": "DRAFT",
    "packagings": [
      {
        "volume_ml": 30,
        "package_type": "BOTTLE"
      }
    ]
  }')
assert_error_contract \
  "API-NEG-002b" \
  "POST /beers (invalid type abv & volume_ml=30 < 50) — Порушення типів і меж (Dynamic 422, AC-001.2)" \
  "422" "${STATUS}" \
  "true"

# 10. API-NEG-003: Non-existent / foreign private resource ID -> Simulated 404 Not Found via Prefer: code=404
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X GET "${BASE_URL}/consumption-entries/c0a80123-9999-4444-9999-0123456789ab" \
  -H "${AUTH_HEADER}" \
  -H "Accept: application/json" \
  -H "Prefer: code=404")
assert_error_contract \
  "API-NEG-003" \
  "GET /consumption-entries/{id} — Звернення до неіснуючого/чужого ID (Prefer: code=404, REQ-NF-005)" \
  "404" "${STATUS}" \
  "false"

# 11. API-NEG-004 (BDD-SCN-004): Business logic conflict / duplicate barcode -> Simulated 409 Conflict
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/beers" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "Prefer: code=409, example=duplicate_barcode_conflict" \
  -d '{
    "name": "Urquell Original",
    "brewery": "Plzensky Prazdroj",
    "style": "Pilsner",
    "abv": 4.4,
    "beer_type": "PACKAGED",
    "packagings": [
      {
        "volume_ml": 500,
        "package_type": "BOTTLE",
        "barcode": "8594404110123"
      }
    ]
  }')
assert_error_contract \
  "API-NEG-004" \
  "POST /beers — Дублікат штрих-коду 8594404110123 (Prefer: code=409, BDD-SCN-004)" \
  "409" "${STATUS}" \
  "false"

# 12. API-NEG-005: Unauthorized access (missing Bearer token) -> Dynamic 401 Unauthorized
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/consumption-entries" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "beer_id": "b0a80123-0001-4444-9999-0123456789ab",
    "packaging_id": "30a80123-0001-4444-9999-0123456789ab",
    "quantity": 1,
    "consumed_at": "2026-10-20T18:00:00Z"
  }')
assert_error_contract \
  "API-NEG-005" \
  "POST /consumption-entries (no Authorization) — Неавторизований доступ (Dynamic 401, REQ-NF-004)" \
  "401" "${STATUS}" \
  "true"

# 13. API-NEG-006 (BDD-SCN-003): Forbidden review without prior consumption -> Simulated 403 Forbidden
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X POST "${BASE_URL}/beers/b0a80123-0003-4444-9999-0123456789ab/reviews" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "Prefer: code=403, example=consumption_required_error" \
  -d '{
    "rating": 4.5,
    "review_text": "Чудовий класичний лагер"
  }')
assert_error_contract \
  "API-NEG-006" \
  "POST /beers/{id}/reviews — Відсутній факт споживання (Prefer: code=403, BDD-SCN-003)" \
  "403" "${STATUS}" \
  "false"

# 14. API-NEG-007 (BDD-SCN-005): Immutable Append-Only Consumption Log -> Simulated 405 Method Not Allowed
STATUS=$(curl -s -S -D "${HDR_FILE}" -o "${BODY_FILE}" -w "%{http_code}" \
  -X GET "${BASE_URL}/consumption-entries/c0a80123-0001-4444-9999-0123456789ab" \
  -H "${AUTH_HEADER}" \
  -H "Accept: application/json" \
  -H "Prefer: code=405, example=append_only_method_not_allowed")
assert_error_contract \
  "API-NEG-007" \
  "Append-Only /consumption-entries/{id} — Заборона модифікації/видалення (Prefer: code=405, BDD-SCN-005)" \
  "405" "${STATUS}" \
  "false"

echo ""
echo "=============================================================================="
echo " SUMMARY: Total=${TOTAL_TESTS} | Passed=${PASSED_TESTS} | Failed=${FAILED_TESTS}"
echo "=============================================================================="

if [[ "${FAILED_TESTS}" -ne 0 ]]; then
  exit 1
fi
