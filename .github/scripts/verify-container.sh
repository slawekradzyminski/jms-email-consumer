#!/usr/bin/env bash

set -euo pipefail

image="${1:-consumer:ci}"
run_id="${GITHUB_RUN_ID:-local}-$$"
network="consumer-contract-${run_id}"
broker="consumer-broker-${run_id}"
mailpit="consumer-mailpit-${run_id}"
consumer="consumer-app-${run_id}"

print_logs() {
  local status=$?
  if [[ $status -ne 0 ]]; then
    echo "Consumer contract verification failed; container logs follow."
    docker logs "$broker" 2>&1 || true
    docker logs "$mailpit" 2>&1 || true
    docker logs "$consumer" 2>&1 || true
  fi
  docker rm -f "$consumer" "$mailpit" "$broker" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  exit "$status"
}
trap print_logs EXIT

docker network create "$network" >/dev/null

docker run --detach --name "$broker" --network "$network" \
  --network-alias activemq \
  --env ARTEMIS_USER=admin \
  --env ARTEMIS_PASSWORD=admin \
  --env ANONYMOUS_LOGIN=false \
  --env 'EXTRA_ARGS=--http-host 0.0.0.0 --relax-jolokia --no-autotune' \
  apache/activemq-artemis:2.42.0 >/dev/null

docker run --detach --name "$mailpit" --network "$network" \
  --network-alias mailpit \
  --env MP_DISABLE_VERSION_CHECK=true \
  axllent/mailpit:v1.30.0 >/dev/null

docker run --detach --name "$consumer" --network "$network" \
  --env SPRING_ACTIVEMQ_BROKER_URL=tcp://activemq:61616 \
  --env SPRING_ACTIVEMQ_USER=admin \
  --env SPRING_ACTIVEMQ_PASSWORD=admin \
  --env SPRING_MAIL_HOST=mailpit \
  --env SPRING_MAIL_PORT=1025 \
  "$image" >/dev/null

message='{"to":"delivery-contract@example.test","subject":"JMS container contract","message":"Artemis to consumer to Mailpit","template":"GENERIC","properties":{}}'
properties='[{"type":"string","key":"_awesome_","value":"EmailDTO"}]'

published=false
for _ in $(seq 1 60); do
  if docker exec "$broker" /var/lib/artemis-instance/bin/artemis producer \
    --url tcp://localhost:61616 \
    --user admin \
    --password admin \
    --destination queue://email \
    --message-count 1 \
    --message "$message" \
    --properties "$properties" \
    --silent >/dev/null 2>&1; then
    published=true
    break
  fi
  sleep 2
done

if [[ "$published" != true ]]; then
  echo "Artemis did not become ready for the contract message" >&2
  exit 1
fi

docker run --rm --interactive --network "$network" python:3.13-alpine python - <<'PY'
import json
import time
import urllib.request

for attempt in range(60):
    try:
        with urllib.request.urlopen("http://mailpit:8025/api/v1/messages", timeout=2) as response:
            payload = json.load(response)
        messages = payload.get("messages", [])
        match = next(
            (message for message in messages
             if message.get("Subject") == "JMS container contract"),
            None,
        )
        if match:
            recipients = [item.get("Address") for item in match.get("To", [])]
            if "delivery-contract@example.test" not in recipients:
                raise AssertionError(f"Unexpected recipients: {recipients}")
            print("Verified Artemis -> consumer container -> Mailpit delivery")
            break
    except (OSError, json.JSONDecodeError):
        pass
    if attempt == 59:
        raise RuntimeError("Mailpit did not receive the contract email")
    time.sleep(2)
PY
