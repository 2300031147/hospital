#!/bin/bash
set -euo pipefail

BASE="http://localhost:8000"
PASS=0
FAIL=0
TOTAL=0

green()  { echo -e "\033[32m✅ PASS: $1\033[0m"; }
red()    { echo -e "\033[31m❌ FAIL: $1\033[0m"; }
yellow() { echo -e "\033[33m⚠️  $1\033[0m"; }
blue()   { echo -e "\033[34m━━━ $1 ━━━\033[0m"; }

assert_status() {
  local desc="$1" expected="$2" actual="$3" body="$4"
  TOTAL=$((TOTAL + 1))
  if [ "$actual" = "$expected" ]; then
    PASS=$((PASS + 1))
    green "$desc (HTTP $actual)"
  else
    FAIL=$((FAIL + 1))
    red "$desc — Expected $expected, got $actual"
    echo "    Response: $(echo "$body" | head -c 200)"
  fi
}

assert_json_field() {
  local desc="$1" body="$2" field="$3" expected="$4"
  TOTAL=$((TOTAL + 1))
  local actual
  actual=$(echo "$body" | jq -r "$field" 2>/dev/null || echo "PARSE_ERROR")
  if [ "$actual" = "$expected" ]; then
    PASS=$((PASS + 1))
    green "$desc — $field = $expected"
  else
    FAIL=$((FAIL + 1))
    red "$desc — $field expected '$expected', got '$actual'"
  fi
}

assert_json_not_empty() {
  local desc="$1" body="$2"
  TOTAL=$((TOTAL + 1))
  local len
  len=$(echo "$body" | jq 'length' 2>/dev/null || echo "0")
  if [ "$len" -gt 0 ] 2>/dev/null; then
    PASS=$((PASS + 1))
    green "$desc — got $len items"
  else
    FAIL=$((FAIL + 1))
    red "$desc — expected non-empty response, got: $(echo "$body" | head -c 200)"
  fi
}

# =====================================================
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║   AEROVHYN Java Backend — E2E Test Suite v2         ║"
echo "║   Running against LIVE server: $BASE        ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# =====================================================
blue "MODULE 1: HEALTH CHECK (Public)"
# =====================================================
RESP=$(curl -s -w "\n%{http_code}" "$BASE/api/health")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/health" "200" "$STATUS" "$BODY"
assert_json_field "Health status" "$BODY" ".status" "ok"
assert_json_field "Health system" "$BODY" ".system" "AEROVHYN"

# =====================================================
blue "MODULE 2: AUTHENTICATION"
# =====================================================

# 2a: Login with valid credentials
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/auth/token (valid login)" "200" "$STATUS" "$BODY"
assert_json_field "Login role" "$BODY" ".role" "command_center"
assert_json_field "Login token_type" "$BODY" ".token_type" "bearer"

ADMIN_TOKEN=$(echo "$BODY" | jq -r '.access_token')
AUTH="Authorization: Bearer $ADMIN_TOKEN"

# 2b: Login with invalid credentials
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrongpassword"}')
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/auth/token (wrong password)" "401" "$STATUS" "$BODY"

# 2c: Access protected endpoint without token
RESP=$(curl -s -w "\n%{http_code}" "$BASE/api/users")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
# Spring Security returns 403 for missing auth on secured endpoints
TOTAL=$((TOTAL + 1))
if [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
  PASS=$((PASS + 1))
  green "GET /api/users (no auth) — blocked (HTTP $STATUS)"
else
  FAIL=$((FAIL + 1))
  red "GET /api/users (no auth) — Expected 401/403, got $STATUS"
fi

# =====================================================
blue "MODULE 3: USER MANAGEMENT (CRUD)"
# =====================================================

# 3a: List users
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/users")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/users" "200" "$STATUS" "$BODY"
assert_json_not_empty "User list" "$BODY"

# 3b: Create a user (snake_case JSON)
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser_e2e","password":"TestPass123!","full_name":"E2E Test User","role":"dispatcher","ambulance_id":null,"hospital_id":null}' \
  "$BASE/api/users")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/users (create)" "200" "$STATUS" "$BODY"
assert_json_field "Created user name" "$BODY" ".username" "testuser_e2e"
NEW_USER_ID=$(echo "$BODY" | jq -r '.id')

# 3c: Update the user
RESP=$(curl -s -w "\n%{http_code}" -X PUT -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser_e2e","password":"TestPass123!","full_name":"E2E Updated","role":"dispatcher"}' \
  "$BASE/api/users/$NEW_USER_ID")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "PUT /api/users/$NEW_USER_ID (update)" "200" "$STATUS" "$BODY"
assert_json_field "Updated user full_name" "$BODY" ".full_name" "E2E Updated"

# 3d: Password reset
RESP=$(curl -s -w "\n%{http_code}" -X PUT -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"new_password":"NewSecurePass1!"}' \
  "$BASE/api/users/$NEW_USER_ID/password")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "PUT /api/users/$NEW_USER_ID/password (reset)" "200" "$STATUS" "$BODY"

# 3e: Login with new user's updated password
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser_e2e","password":"NewSecurePass1!"}')
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/auth/token (login as new user)" "200" "$STATUS" "$BODY"
assert_json_field "New user role" "$BODY" ".role" "dispatcher"

# 3f: Self-delete prevention
ADMIN_ID=$(curl -s -H "$AUTH" "$BASE/api/users" | jq -r '.[] | select(.username=="admin") | .id')
RESP=$(curl -s -w "\n%{http_code}" -X DELETE -H "$AUTH" "$BASE/api/users/$ADMIN_ID")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "DELETE /api/users/self (self-delete blocked)" "400" "$STATUS" "$BODY"

# 3g: Delete created user
RESP=$(curl -s -w "\n%{http_code}" -X DELETE -H "$AUTH" "$BASE/api/users/$NEW_USER_ID")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "DELETE /api/users/$NEW_USER_ID" "200" "$STATUS" "$BODY"

# =====================================================
blue "MODULE 4: HOSPITALS (CRUD + Actions)"
# =====================================================

# 4a: List hospitals
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/hospitals")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/hospitals" "200" "$STATUS" "$BODY"
HOSPITAL_COUNT=$(echo "$BODY" | jq 'length')
assert_json_not_empty "Hospital list" "$BODY"

# 4b: Get single hospital
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/hospitals/1")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/hospitals/1" "200" "$STATUS" "$BODY"

# 4c: Create hospital (snake_case)
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"name":"E2E Test Hospital","lat":17.5,"lon":78.5,"icu_beds":5,"total_icu_beds":10,"ventilators":3,"total_ventilators":6,"specialists":["trauma","general"],"current_load":10,"max_capacity":50,"equipment_score":0.85,"status":"active"}' \
  "$BASE/api/hospitals")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/hospitals (create)" "200" "$STATUS" "$BODY"
assert_json_field "Created hospital" "$BODY" ".name" "E2E Test Hospital"
NEW_HOSP_ID=$(echo "$BODY" | jq -r '.id')

# 4d: Update hospital
RESP=$(curl -s -w "\n%{http_code}" -X PUT -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"icu_beds":3,"current_load":20}' \
  "$BASE/api/hospitals/$NEW_HOSP_ID")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "PUT /api/hospitals/$NEW_HOSP_ID (update)" "200" "$STATUS" "$BODY"

# 4e: Acknowledge handoff
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  "$BASE/api/hospitals/1/acknowledge")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/hospitals/1/acknowledge" "200" "$STATUS" "$BODY"
assert_json_field "Acknowledge status" "$BODY" ".status" "acknowledged"

# 4f: Discharge patient
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  "$BASE/api/hospitals/1/discharge")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/hospitals/1/discharge" "200" "$STATUS" "$BODY"

# 4g: Get hospital 404
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/hospitals/99999")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/hospitals/99999 (not found)" "404" "$STATUS" "$BODY"

# 4h: Delete test hospital
RESP=$(curl -s -w "\n%{http_code}" -X DELETE -H "$AUTH" "$BASE/api/hospitals/$NEW_HOSP_ID")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "DELETE /api/hospitals/$NEW_HOSP_ID" "200" "$STATUS" "$BODY"

# =====================================================
blue "MODULE 5: AMBULANCES"
# =====================================================

# 5a: List ambulances
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/ambulances")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/ambulances" "200" "$STATUS" "$BODY"
assert_json_not_empty "Ambulance list" "$BODY"

# 5b: Create ambulance
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"name":"AMB-E2E","lat":17.42,"lon":78.44}' \
  "$BASE/api/ambulances")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/ambulances (create)" "200" "$STATUS" "$BODY"
NEW_AMB_ID=$(echo "$BODY" | jq -r '.id')

# 5c: Update ambulance position
RESP=$(curl -s -w "\n%{http_code}" -X PUT -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"lat":17.43,"lon":78.45}' \
  "$BASE/api/ambulances/$NEW_AMB_ID/position")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "PUT /api/ambulances/$NEW_AMB_ID/position" "200" "$STATUS" "$BODY"
assert_json_field "Position update" "$BODY" ".status" "updated"

# =====================================================
blue "MODULE 6: SEVERITY CLASSIFICATION"
# =====================================================

# 6a: Critical patient (snake_case + lowercase enum)
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"heart_rate":150,"spo2":82,"systolic_bp":70,"emergency_type":"cardiac","age":65}' \
  "$BASE/api/classify")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/classify (critical vitals)" "200" "$STATUS" "$BODY"
assert_json_field "Severity level" "$BODY" ".level" "critical"

# 6b: Stable patient
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"heart_rate":75,"spo2":98,"systolic_bp":120,"emergency_type":"general","age":30}' \
  "$BASE/api/classify")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/classify (stable vitals)" "200" "$STATUS" "$BODY"
assert_json_field "Severity level" "$BODY" ".level" "stable"

# 6c: Moderate patient
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"heart_rate":130,"spo2":91,"systolic_bp":85,"emergency_type":"trauma","age":45}' \
  "$BASE/api/classify")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/classify (moderate vitals)" "200" "$STATUS" "$BODY"
SEVERITY=$(echo "$BODY" | jq -r '.level')
TOTAL=$((TOTAL + 1))
if [ "$SEVERITY" = "moderate" ] || [ "$SEVERITY" = "critical" ]; then
  PASS=$((PASS + 1))
  green "Moderate/Critical classification ($SEVERITY)"
else
  FAIL=$((FAIL + 1))
  red "Expected moderate or critical, got $SEVERITY"
fi

# =====================================================
blue "MODULE 7: ROUTING & DISPATCH"
# =====================================================

# 7a: Route a critical patient (command_center passes ambulance_id in body)
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d "{\"ambulance_lat\":17.43,\"ambulance_lon\":78.45,\"vitals\":{\"heart_rate\":160,\"spo2\":78,\"systolic_bp\":65,\"emergency_type\":\"cardiac\",\"age\":70},\"ambulance_id\":$NEW_AMB_ID}" \
  "$BASE/api/route")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/route (critical cardiac)" "200" "$STATUS" "$BODY"
TOTAL=$((TOTAL + 1))
RANKED_COUNT=$(echo "$BODY" | jq '.ranked_hospitals | length' 2>/dev/null || echo "0")
if [ "$RANKED_COUNT" -gt 0 ] 2>/dev/null; then
  PASS=$((PASS + 1))
  green "Route returned $RANKED_COUNT ranked hospitals"
else
  FAIL=$((FAIL + 1))
  red "Route returned no ranked hospitals: $(echo "$BODY" | head -c 300)"
fi

# 7b: Route a stable patient
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d "{\"ambulance_lat\":17.44,\"ambulance_lon\":78.49,\"vitals\":{\"heart_rate\":80,\"spo2\":97,\"systolic_bp\":115,\"emergency_type\":\"general\",\"age\":25},\"ambulance_id\":$NEW_AMB_ID}" \
  "$BASE/api/route")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/route (stable general)" "200" "$STATUS" "$BODY"

# =====================================================
blue "MODULE 8: SYSTEM SETTINGS"
# =====================================================

# 8a: Get settings
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/settings")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/settings" "200" "$STATUS" "$BODY"

# 8b: Update settings (snake_case)
RESP=$(curl -s -w "\n%{http_code}" -X PUT -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"distance_weight":0.3,"readiness_weight":0.4,"severity_match_weight":0.3,"max_routing_distance_km":50}' \
  "$BASE/api/settings")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "PUT /api/settings (update)" "200" "$STATUS" "$BODY"

# 8c: Settings with invalid weights (should fail)
RESP=$(curl -s -w "\n%{http_code}" -X PUT -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"distance_weight":0.5,"readiness_weight":0.5,"severity_match_weight":0.5,"max_routing_distance_km":50}' \
  "$BASE/api/settings")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "PUT /api/settings (invalid weights rejected)" "400" "$STATUS" "$BODY"

# 8d: Restore default settings
RESP=$(curl -s -w "\n%{http_code}" -X PUT -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"distance_weight":0.2,"readiness_weight":0.5,"severity_match_weight":0.3,"max_routing_distance_km":30}' \
  "$BASE/api/settings")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "PUT /api/settings (restore defaults)" "200" "$STATUS" "$BODY"

# =====================================================
blue "MODULE 9: SIMULATION"
# =====================================================

# 9a: Simulate overload
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" "$BASE/api/simulate/overload/2")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/simulate/overload/2" "200" "$STATUS" "$BODY"
assert_json_field "Overload status" "$BODY" ".status" "overloaded"

# 9b: Verify hospital 2 is now at high load
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/hospitals/2")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/hospitals/2 (post-overload)" "200" "$STATUS" "$BODY"
LOAD=$(echo "$BODY" | jq '.current_load')
MAX=$(echo "$BODY" | jq '.max_capacity')
TOTAL=$((TOTAL + 1))
if [ "$LOAD" -ge "$((MAX * 90 / 100))" ] 2>/dev/null; then
  PASS=$((PASS + 1))
  green "Hospital 2 load verified overloaded ($LOAD / $MAX)"
else
  FAIL=$((FAIL + 1))
  red "Hospital 2 load not overloaded ($LOAD / $MAX)"
fi

# 9c: Reset simulation
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" "$BASE/api/simulate/reset")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/simulate/reset" "200" "$STATUS" "$BODY"
assert_json_field "Reset status" "$BODY" ".status" "reset"

# Re-login after reset (users are re-seeded)
ADMIN_TOKEN=$(curl -s -X POST "$BASE/api/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.access_token')
AUTH="Authorization: Bearer $ADMIN_TOKEN"

# 9d: Verify hospital load is restored
sleep 1
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/hospitals/2")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
LOAD_AFTER=$(echo "$BODY" | jq '.current_load')
MAX2=$(echo "$BODY" | jq '.max_capacity')
TOTAL=$((TOTAL + 1))
if [ "$LOAD_AFTER" -lt "$((MAX2 * 90 / 100))" ] 2>/dev/null; then
  PASS=$((PASS + 1))
  green "Hospital 2 load restored after reset ($LOAD_AFTER / $MAX2)"
else
  FAIL=$((FAIL + 1))
  red "Hospital 2 load NOT restored after reset ($LOAD_AFTER / $MAX2)"
fi

# =====================================================
blue "MODULE 10: ANALYTICS & LOGS"
# =====================================================

# 10a: Get analytics
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/analytics")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/analytics" "200" "$STATUS" "$BODY"

# 10b: Get logs
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/logs?limit=10")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/logs" "200" "$STATUS" "$BODY"

# 10c: Get audit log (blockchain)
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/audit-log?limit=10")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/audit-log" "200" "$STATUS" "$BODY"

# 10d: Verify blockchain integrity
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/audit-log/verify")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/audit-log/verify" "200" "$STATUS" "$BODY"
assert_json_field "Blockchain valid" "$BODY" ".valid" "true"

# =====================================================
blue "MODULE 11: NOTIFICATIONS"
# =====================================================

RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" "$BASE/api/notifications/test")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/notifications/test" "200" "$STATUS" "$BODY"
assert_json_field "Notification sent" "$BODY" ".status" "sent"

# =====================================================
blue "MODULE 12: WEBSOCKET METRICS"
# =====================================================

RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/ws/metrics")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "GET /api/ws/metrics" "200" "$STATUS" "$BODY"
assert_json_field "WS metrics active" "$BODY" ".status" "active"

# =====================================================
blue "MODULE 13: RATE LIMITING (Login Brute-Force)"
# =====================================================

yellow "Testing failed login rate limiting (5 attempts)..."
for i in {1..5}; do
  curl -s -X POST "$BASE/api/auth/token" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"brute_force_attempt"}' > /dev/null
done
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"brute_force_attempt"}')
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "POST /api/auth/token (rate limited after 5 fails)" "429" "$STATUS" "$BODY"
TOTAL=$((TOTAL + 1))
MSG=$(echo "$BODY" | jq -r '.detail // .message // .error // ""' 2>/dev/null)
if echo "$MSG" | grep -qi "too many\|rate\|limit"; then
  PASS=$((PASS + 1))
  green "Rate limit message present: $MSG"
else
  FAIL=$((FAIL + 1))
  red "Rate limit message not found: $MSG"
fi

# Clean up rate limit key in Redis (valkey)
valkey-cli DEL "failed_logins:127.0.0.1" "failed_logins:0:0:0:0:0:0:0:1" "password_reset:127.0.0.1" "password_reset:0:0:0:0:0:0:0:1" > /dev/null 2>&1 || redis-cli DEL "failed_logins:127.0.0.1" "failed_logins:0:0:0:0:0:0:0:1" "password_reset:127.0.0.1" "password_reset:0:0:0:0:0:0:0:1" > /dev/null 2>&1 || true
curl -s "$BASE/api/health/reset-limits" > /dev/null
sleep 1

# =====================================================
blue "MODULE 14: SECURITY HEADERS"
# =====================================================

HEADERS=$(curl -sI "$BASE/api/health")
TOTAL=$((TOTAL + 1))
if echo "$HEADERS" | grep -qi "X-Content-Type-Options"; then
  PASS=$((PASS + 1))
  green "Security header X-Content-Type-Options present"
else
  FAIL=$((FAIL + 1))
  red "Security header X-Content-Type-Options missing"
fi
TOTAL=$((TOTAL + 1))
if echo "$HEADERS" | grep -qi "X-Frame-Options"; then
  PASS=$((PASS + 1))
  green "Security header X-Frame-Options present"
else
  FAIL=$((FAIL + 1))
  red "Security header X-Frame-Options missing"
fi

# =====================================================
blue "MODULE 15: AUTHORIZATION / ROLE-BASED ACCESS"
# =====================================================

# Login as paramedic user
PARA_RESP=$(curl -s -X POST "$BASE/api/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"paramedic1","password":"rescue123"}')
PARA_TOKEN=$(echo "$PARA_RESP" | jq -r '.access_token // empty')
if [ -n "$PARA_TOKEN" ]; then
  PARA_AUTH="Authorization: Bearer $PARA_TOKEN"

  # Paramedic should NOT access users
  RESP=$(curl -s -w "\n%{http_code}" -H "$PARA_AUTH" "$BASE/api/users")
  STATUS=$(echo "$RESP" | tail -1)
  assert_status "GET /api/users (as paramedic — forbidden)" "403" "$STATUS" ""

  # Paramedic should NOT access simulation
  RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$PARA_AUTH" "$BASE/api/simulate/overload/1")
  STATUS=$(echo "$RESP" | tail -1)
  assert_status "POST /api/simulate (as paramedic — forbidden)" "403" "$STATUS" ""

  # Paramedic CAN classify
  RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$PARA_AUTH" \
    -H "Content-Type: application/json" \
    -d '{"heart_rate":80,"spo2":97,"systolic_bp":120,"emergency_type":"general","age":30}' \
    "$BASE/api/classify")
  STATUS=$(echo "$RESP" | tail -1)
  assert_status "POST /api/classify (as paramedic — allowed)" "200" "$STATUS" ""
else
  yellow "Paramedic user not seeded — skipping RBAC tests"
fi

# =====================================================
blue "MODULE 16: COMPLETE DISPATCH WORKFLOW"
# =====================================================

# Re-login as admin
LOGIN_RESP=$(curl -s -X POST "$BASE/api/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
ADMIN_TOKEN=$(echo "$LOGIN_RESP" | jq -r '.access_token')
AUTH="Authorization: Bearer $ADMIN_TOKEN"

# Step 1: Create ambulance
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"name":"AMB-WORKFLOW","lat":17.425,"lon":78.45}' \
  "$BASE/api/ambulances")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
WF_AMB_ID=$(echo "$BODY" | jq -r '.id')
assert_status "Workflow: create ambulance" "200" "$STATUS" "$BODY"

# Step 2: Route with critical vitals
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d "{\"ambulance_lat\":17.425,\"ambulance_lon\":78.45,\"vitals\":{\"heart_rate\":180,\"spo2\":75,\"systolic_bp\":60,\"emergency_type\":\"cardiac\",\"age\":72},\"ambulance_id\":$WF_AMB_ID}" \
  "$BASE/api/route")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "Workflow: route critical patient" "200" "$STATUS" "$BODY"
DEST_HOSP_ID=$(echo "$BODY" | jq -r '.selected_hospital.id // empty')
TOTAL=$((TOTAL + 1))
if [ -n "$DEST_HOSP_ID" ]; then
  PASS=$((PASS + 1))
  green "Workflow: assigned to hospital $DEST_HOSP_ID"
else
  FAIL=$((FAIL + 1))
  red "Workflow: no hospital assigned. Body: $(echo "$BODY" | head -c 300)"
fi

# Step 3: Acknowledge at hospital
if [ -n "$DEST_HOSP_ID" ]; then
  RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" "$BASE/api/hospitals/$DEST_HOSP_ID/acknowledge")
  STATUS=$(echo "$RESP" | tail -1)
  assert_status "Workflow: hospital acknowledge" "200" "$STATUS" ""
fi

# Step 4: Complete the run
RESP=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" "$BASE/api/ambulances/$WF_AMB_ID/complete")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
assert_status "Workflow: complete ambulance run" "200" "$STATUS" "$BODY"

# Step 5: Verify ambulance is back to idle
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/ambulances")
STATUS=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')
WF_AMB_STATUS=$(echo "$BODY" | jq -r ".[] | select(.id==$WF_AMB_ID) | .status")
TOTAL=$((TOTAL + 1))
if [ "$WF_AMB_STATUS" = "idle" ]; then
  PASS=$((PASS + 1))
  green "Workflow: ambulance $WF_AMB_ID back to idle"
else
  FAIL=$((FAIL + 1))
  red "Workflow: ambulance $WF_AMB_ID status is '$WF_AMB_STATUS', expected 'idle'"
fi

# =====================================================
# Final cleanup: reset to pristine state
# =====================================================
curl -s -X POST -H "$AUTH" "$BASE/api/simulate/reset" > /dev/null

# =====================================================
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║              E2E TEST RESULTS SUMMARY               ║"
echo "╠══════════════════════════════════════════════════════╣"
printf "║  Total:  %-42s ║\n" "$TOTAL"
printf "║  \033[32mPassed: %-42s\033[0m ║\n" "$PASS"
printf "║  \033[31mFailed: %-42s\033[0m ║\n" "$FAIL"
echo "╠══════════════════════════════════════════════════════╣"
if [ "$FAIL" -eq 0 ]; then
  echo -e "║  \033[32m🎉  ALL TESTS PASSED — SERVER IS BUG-FREE!  🎉\033[0m      ║"
else
  echo -e "║  \033[31m⚠️   $FAIL TEST(S) FAILED — SEE ABOVE FOR DETAILS\033[0m     ║"
fi
echo "╚══════════════════════════════════════════════════════╝"
echo ""

exit $FAIL
