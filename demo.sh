#!/usr/bin/env bash
# Live demo of the Feature Flag Service
# Usage: ./demo.sh                  (uses the live DigitalOcean app)
#        ./demo.sh http://localhost:8080
URL=${1:-https://feature-flag-service-cq939.ondigitalocean.app}
H='Content-Type: application/json'
F="demo-$(date +%s)"   # unique flag name every run, so no 409 surprises

step() { echo; echo "== $1 =="; }

step "1. Health check"
curl -s $URL/actuator/health; echo

step "2. Create flag '$F' (expect 201)"
curl -s -o /dev/null -w "%{http_code}\n" -X POST $URL/api/v1/flags -H "$H" \
  -d "{\"name\":\"$F\",\"description\":\"Demo flag\",\"defaultEnabled\":false}"

step "3. Same name again (expect 409)"
curl -s -o /dev/null -w "%{http_code}\n" -X POST $URL/api/v1/flags -H "$H" \
  -d "{\"name\":\"$F\",\"defaultEnabled\":false}"

step "4. Flag is OFF globally -> alice false (GLOBAL)"
curl -s "$URL/api/v1/flags/$F/evaluate?userId=alice"; echo

step "5. Turn ON only for alice (user override)"
curl -s -X PUT $URL/api/v1/flags/$F/users/alice -H "$H" -d '{"enabled":true}'; echo

step "6. alice true (USER_OVERRIDE), bob still false"
curl -s "$URL/api/v1/flags/$F/evaluate?userId=alice"; echo
curl -s "$URL/api/v1/flags/$F/evaluate?userId=bob"; echo

step "7. Turn ON globally with 50% rollout"
curl -s -o /dev/null -X PUT $URL/api/v1/flags/$F/global -H "$H" -d '{"enabled":true}'
curl -s -X PUT $URL/api/v1/flags/$F/rollout -H "$H" -d '{"percentage":50}'; echo

step "8. Ten users -> about half enabled (ROLLOUT)"
for u in u1 u2 u3 u4 u5 u6 u7 u8 u9 u10; do
  curl -s "$URL/api/v1/flags/$F/evaluate?userId=$u"; echo
done

step "9. All flags for alice in one call"
curl -s $URL/api/v1/users/alice/flags; echo

step "10. Errors: unknown flag (404), bad name (400), missing userId (400)"
curl -s -o /dev/null -w "%{http_code}\n" $URL/api/v1/flags/nope
curl -s -o /dev/null -w "%{http_code}\n" -X POST $URL/api/v1/flags -H "$H" -d '{"name":"Bad Name!","defaultEnabled":true}'
curl -s -o /dev/null -w "%{http_code}\n" "$URL/api/v1/flags/$F/evaluate"

step "11. Error body format (RFC 9457 ProblemDetail)"
curl -s $URL/api/v1/flags/nope; echo
