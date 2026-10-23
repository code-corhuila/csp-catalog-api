#!/usr/bin/env sh
# DEVELOPMENT ONLY. Creates synthetic catalog data through the API of the running platform, so that another service (the
# seat map of Booking, the portals) can be tried against the real Catalog: one published movie, a room whose seats have
# row and number, a room whose seats do not, and one scheduled showtime in each room. It prints the ids.
# It can be run again: a movie, a room or a showtime that already exists is reused, never duplicated.
#
# It signs an administrator token with the development private key (the one ./scripts/dev-keys.sh of csp-infra-postgres
# writes to keys/, never a real key) and calls the API from inside the container, because the service is not published.
#
# usage: ./scripts/seed-dev.sh
# variables: JWT_PRIVATE_KEY_FILE  (default ../csp-infra-postgres/keys/jwt-private.pem)
#            CATALOG_API_CONTAINER (default: the running container of the service catalog-api)
#            SEED_DAYS_AHEAD       (default 30: how far from today a new showtime starts)
# needs: docker, openssl and python (3, or `python`) to read the JSON answers.
set -eu
cd "$(dirname "$0")/.."

key="${JWT_PRIVATE_KEY_FILE:-../csp-infra-postgres/keys/jwt-private.pem}"
[ -f "$key" ] || { echo "missing $key: run ./scripts/dev-keys.sh in csp-infra-postgres, or set JWT_PRIVATE_KEY_FILE"; exit 1; }
container="${CATALOG_API_CONTAINER:-$(docker ps -q --filter label=com.docker.compose.service=catalog-api | head -n 1)}"
[ -n "$container" ] || { echo "no running catalog-api container: start it from the platform composition first"; exit 1; }
py=$(command -v python3 || command -v python) || { echo "python is required to read the JSON answers"; exit 1; }
days="${SEED_DAYS_AHEAD:-30}"
base="http://localhost:8082/api/v1/catalog"
# the token is signed through two files kept next to the key (git-ignored there): a native openssl would rewrite the
# bytes of a binary pipe. The sub of a user token must be the UUID of a user: this one is only a development value.
work="$(dirname "$key")"
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
now=$(date +%s)
header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
payload=$(printf '{"sub":"7f3b1c2a-4d5e-4f60-8a9b-0c1d2e3f4a5b","permissions":["catalog:write","room:write","showtime:write"],"iat":%s,"exp":%s}' "$now" "$((now + 900))" | b64url)
printf '%s.%s' "$header" "$payload" > "$work/.seed-message.txt"
openssl dgst -sha256 -sign "$key" -out "$work/.seed-signature.bin" "$work/.seed-message.txt"
token="$header.$payload.$(openssl base64 -A -in "$work/.seed-signature.bin" | tr '+/' '-_' | tr -d '=')"

# call METHOD PATH [BODY]: prints the body, then the status on the last line
call() {
  if [ "$#" -ge 3 ]; then
    docker exec "$container" curl -s -w '\n%{http_code}' -X "$1" "$base$2" -H "Authorization: Bearer $token" \
      -H "Idempotency-Key: $("$py" -c 'import uuid; print(uuid.uuid4())')" -H 'Content-Type: application/json' -d "$3"
  else
    docker exec "$container" curl -s -w '\n%{http_code}' -X "$1" "$base$2" -H "Authorization: Bearer $token"
  fi
}
status_of() { printf '%s\n' "$1" | tail -n 1; }
body_of() { printf '%s\n' "$1" | sed '$d'; }
field() { printf '%s' "$1" | "$py" -c "import sys, json; print(json.load(sys.stdin)['$2'])"; }

# find_id RESOURCE PYTHON-CONDITION-ON-item: the id of the first item of the resource that satisfies the condition
find_id() {
  page=1
  while :; do
    answer=$(call GET "/$1?page=$page&limit=100")
    [ "$(status_of "$answer")" = 200 ] || { echo "GET /$1 answered $(status_of "$answer")" >&2; exit 1; }
    found=$(body_of "$answer" | "$py" -c "
import sys, json
d = json.load(sys.stdin)
print(next((item['id'] for item in d['data'] if $2), ''))")
    if [ -n "$found" ]; then printf '%s' "$found"; return; fi
    last=$(body_of "$answer" | "$py" -c "import sys, json; print(json.load(sys.stdin)['meta']['totalPages'])")
    [ "$page" -lt "$last" ] || return 0
    page=$((page + 1))
  done
}

# create RESOURCE BODY: posts and prints the id of the created item
create() {
  answer=$(call POST "/$1" "$2")
  [ "$(status_of "$answer")" = 201 ] || { echo "POST /$1 answered $(status_of "$answer"): $(body_of "$answer")" >&2; exit 1; }
  field "$(body_of "$answer")" id
}

movie_title="Synthetic Seed Movie"
movie=$(find_id movies "item['title'] == '$movie_title'")
[ -n "$movie" ] || movie=$(create movies '{"title":"'"$movie_title"'","synopsis":"Synthetic data of the development seed.","durationMinutes":120,"classification":"PG-13","releaseDate":"2027-01-15","genres":["Drama"],"cast":["Synthetic Actor"],"posterUrl":"https://example.com/seed-poster.png","status":"PUBLISHED"}')

rows_name="Seed Room With Rows"
plain_name="Seed Room Without Rows"
rows_room=$(find_id rooms "item['name'] == '$rows_name'")
[ -n "$rows_room" ] || rows_room=$(create rooms '{"name":"'"$rows_name"'","seats":[{"label":"A1","row":"A","number":1,"type":"STANDARD"},{"label":"A2","row":"A","number":2,"type":"STANDARD"},{"label":"B1","row":"B","number":1,"type":"VIP"},{"label":"B2","row":"B","number":2,"type":"ACCESSIBLE"}]}')
plain_room=$(find_id rooms "item['name'] == '$plain_name'")
[ -n "$plain_room" ] || plain_room=$(create rooms '{"name":"'"$plain_name"'","seats":[{"label":"S1","type":"STANDARD"},{"label":"S2","type":"VIP"},{"label":"S3"}]}')

# showtime ROOM-ID START-OFFSET-DAYS PRICE: reuses a scheduled showtime of the movie in that room
showtime() {
  existing=$(find_id showtimes "item['movieId'] == '$movie' and item['roomId'] == '$1' and item['status'] == 'SCHEDULED'")
  if [ -n "$existing" ]; then printf '%s' "$existing"; return; fi
  starts=$("$py" -c "import datetime; d = datetime.datetime.now(datetime.timezone.utc).replace(hour=20, minute=0, second=0, microsecond=0) + datetime.timedelta(days=$days + $2); print(d.strftime('%Y-%m-%dT%H:%M:%SZ')); print((d + datetime.timedelta(hours=2)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
  from=$(printf '%s\n' "$starts" | sed -n 1p)
  to=$(printf '%s\n' "$starts" | sed -n 2p)
  create showtimes '{"movieId":"'"$movie"'","roomId":"'"$1"'","startsAt":"'"$from"'","endsAt":"'"$to"'","priceCents":'"$3"',"audioLanguage":"es","subtitleLanguage":"en"}'
}
rows_showtime=$(showtime "$rows_room" 0 1500)
plain_showtime=$(showtime "$plain_room" 1 1200)

echo "Synthetic catalog data (reused when it already existed):"
echo "  movie                          $movie"
echo "  showtime, room with row/number $rows_showtime   (seats A1 A2 B1 B2)"
echo "  showtime, room without them    $plain_showtime   (seats S1 S2 S3)"
echo "Availability: GET /api/v1/catalog/showtimes/<id>/availability with a service token (sub booking-service)."
