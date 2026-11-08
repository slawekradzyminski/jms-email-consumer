#!/usr/bin/env bash
#
# End-to-end smoke test for a consumer container image (native or JVM):
# Artemis (OpenWire) -> consumer -> Mailpit (SMTP), on a disposable Docker network.
#
# Usage: scripts/native-smoke.sh [image]   (default: consumer:native)
#
# Publishes three messages with the producer's `_awesome_` type header:
#   1. the contract fixture src/test/resources/contracts/email-jms-contract.json
#      (PASSWORD_RESET_REQUESTED, fully qualified type id)  -> rendered reset email
#   2. a GENERIC message (short `EmailDTO` type id)          -> subject/body passed through
#   3. an unknown template value                             -> EmailTemplate.fromValue (@JsonCreator) maps
#                                                               it to GENERIC: delivered with its own subject/body
# followed by a GENERIC sentinel proving the listener keeps consuming in order.
# Reports startup time, image size and container memory (docker stats) at the end.
# Requires docker, curl and python3 on the host.

set -euo pipefail

image="${1:-consumer:native}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="${repo_root}/src/test/resources/contracts/email-jms-contract.json"
run_id="${GITHUB_RUN_ID:-local}-$$"
network="consumer-smoke-${run_id}"
broker="consumer-smoke-broker-${run_id}"
mailpit="consumer-smoke-mailpit-${run_id}"
consumer="consumer-smoke-app-${run_id}"

artemis_image="apache/artemis:2.55.0@sha256:a192eee9c46e6352625ca9dbf0beebe4a34c363111c628bbb10c4aff1227341d"
mailpit_image="axllent/mailpit:v1.30.0@sha256:0059ef81e492a7192af3816281eed6859eb078bd7bdc58b76757c13e10e53a7d"

cleanup() {
  local status=$?
  if [[ $status -ne 0 ]]; then
    echo "Smoke test failed; consumer logs follow." >&2
    docker logs "$consumer" 2>&1 | tail -200 >&2 || true
  fi
  docker rm -f "$consumer" "$mailpit" "$broker" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  exit "$status"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

docker network create "$network" >/dev/null

docker run --detach --name "$broker" --network "$network" --network-alias activemq \
  --env ARTEMIS_USER=admin --env ARTEMIS_PASSWORD=admin --env ANONYMOUS_LOGIN=false \
  --env 'EXTRA_ARGS=--http-host 0.0.0.0 --relax-jolokia --no-autotune' \
  "$artemis_image" >/dev/null

docker run --detach --name "$mailpit" --network "$network" --network-alias mailpit \
  --publish 127.0.0.1::8025 --env MP_DISABLE_VERSION_CHECK=true \
  "$mailpit_image" >/dev/null

mailpit_url="http://$(docker port "$mailpit" 8025/tcp | head -n1)"

publish() {
  local payload="$1" type_id="$2"
  docker exec "$broker" /var/lib/artemis-instance/bin/artemis producer \
    --url tcp://localhost:61616 --user admin --password admin \
    --destination queue://email --message-count 1 \
    --message "$payload" \
    --properties "[{\"type\":\"string\",\"key\":\"_awesome_\",\"value\":\"${type_id}\"}]" \
    --silent >/dev/null 2>&1
}

# Wait for the broker before starting the consumer so the startup measurement excludes broker boot.
for attempt in $(seq 1 60); do
  if docker exec "$broker" /var/lib/artemis-instance/bin/artemis queue stat \
      --url tcp://localhost:61616 --user admin --password admin >/dev/null 2>&1; then
    break
  fi
  [[ $attempt -eq 60 ]] && fail "Artemis did not become ready"
  sleep 2
done

docker run --detach --name "$consumer" --network "$network" \
  --publish 127.0.0.1::4002 \
  --env SPRING_ACTIVEMQ_BROKER_URL=tcp://activemq:61616 \
  --env SPRING_ACTIVEMQ_USER=admin \
  --env SPRING_ACTIVEMQ_PASSWORD=admin \
  --env SPRING_MAIL_HOST=mailpit \
  --env SPRING_MAIL_PORT=1025 \
  "$image" >/dev/null

started_line=""
for attempt in $(seq 1 120); do
  started_line="$(docker logs "$consumer" 2>&1 | grep -m1 'Started EmailApplication' || true)"
  [[ -n "$started_line" ]] && break
  [[ "$(docker inspect -f '{{.State.Running}}' "$consumer")" == true ]] || fail "consumer exited during startup"
  [[ $attempt -eq 120 ]] && fail "consumer did not start within 60s"
  sleep 0.5
done
startup="$(sed -E 's/.*Started EmailApplication in ([0-9.]+) seconds.*/\1/' <<<"$started_line")"
consumer_url="http://$(docker port "$consumer" 4002/tcp | head -n1)"

contract_payload="$(python3 -c 'import json,sys; print(json.dumps(json.load(open(sys.argv[1]))["payload"]))' "$fixture")"
contract_type_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["typeId"])' "$fixture")"

publish "$contract_payload" "$contract_type_id"
publish '{"to":"generic@example.test","subject":"Native smoke generic","message":"Generic body from the smoke test","template":"GENERIC","properties":{}}' EmailDTO
publish '{"to":"unknown-template@example.test","subject":"Native smoke unknown template","message":"Unknown template delivered as GENERIC","template":"NOT_A_TEMPLATE","properties":{}}' EmailDTO
publish '{"to":"sentinel@example.test","subject":"Native smoke sentinel","message":"Delivered after the rejected message","template":"GENERIC"}' EmailDTO

# Mailpit assertions: rendered contract email, generic pass-through, unknown template as GENERIC, sentinel.
python3 - "$mailpit_url" "$fixture" <<'PY'
import json, sys, time, urllib.request

base, fixture = sys.argv[1], sys.argv[2]
payload = json.load(open(fixture))["payload"]
expected = {
    "Password reset requested": payload["to"],
    "Native smoke generic": "generic@example.test",
    "Native smoke unknown template": "unknown-template@example.test",
    "Native smoke sentinel": "sentinel@example.test",
}

def get(path):
    with urllib.request.urlopen(base + path, timeout=5) as response:
        return json.load(response)

messages = []
for _ in range(60):
    messages = get("/api/v1/messages").get("messages", [])
    if set(expected) <= {m.get("Subject") for m in messages}:
        break
    time.sleep(1)
else:
    raise SystemExit(f"FAIL: Mailpit subjects {[m.get('Subject') for m in messages]}, expected {sorted(expected)}")

by_subject = {m["Subject"]: m for m in messages}
for subject, recipient in expected.items():
    recipients = [to.get("Address") for to in by_subject[subject].get("To", [])]
    if recipients != [recipient]:
        raise SystemExit(f"FAIL: {subject!r} went to {recipients}, expected [{recipient}]")
    sender = by_subject[subject].get("From", {}).get("Address")
    if sender != "awesome@testing.com":
        raise SystemExit(f"FAIL: {subject!r} sent from {sender}")

reset = get("/api/v1/message/" + by_subject["Password reset requested"]["ID"])["Text"]
props = payload["properties"]
for fragment in (f"Hi {props['username']},", f"within {props['expiresInMinutes']} minutes", props["resetLink"]):
    if fragment not in reset:
        raise SystemExit(f"FAIL: rendered reset email lacks {fragment!r}:\n{reset}")
generic = get("/api/v1/message/" + by_subject["Native smoke generic"]["ID"])["Text"]
if "Generic body from the smoke test" not in generic:
    raise SystemExit(f"FAIL: generic body not passed through:\n{generic}")

unknown = get("/api/v1/message/" + by_subject["Native smoke unknown template"]["ID"])["Text"]
if "Unknown template delivered as GENERIC" not in unknown:
    raise SystemExit(f"FAIL: unknown template was not rendered as GENERIC:\n{unknown}")
if len(messages) != len(expected):
    raise SystemExit(f"FAIL: expected exactly {len(expected)} emails, got {[m['Subject'] for m in messages]}")

print("Mailpit received:")
for message in sorted(messages, key=lambda m: m["Created"]):
    print(f"  - {message['Subject']} -> {[to['Address'] for to in message['To']]}")
PY

logs="$(docker logs "$consumer" 2>&1)"
grep -q 'Sent GENERIC email to unknown-template@example.test' <<<"$logs" \
  || fail "unknown template was not sent as GENERIC"
if grep -q 'ListenerExecutionFailedException' <<<"$logs"; then
  fail "a listener invocation failed (unknown template must not be rejected or redelivered)"
fi
if grep -Eq 'MissingReflectionRegistration|MissingResourceRegistration|ClassNotFoundException|NoSuchMethodException|UnsupportedFeatureError' <<<"$logs"; then
  grep -E 'MissingReflectionRegistration|MissingResourceRegistration|ClassNotFoundException|NoSuchMethodException|UnsupportedFeatureError' <<<"$logs" | head -5 >&2
  fail "native-image metadata gap reported in consumer logs"
fi
[[ "$(docker inspect -f '{{.State.Running}}' "$consumer")" == true ]] || fail "consumer is no longer running"

health="$(curl --silent --fail "${consumer_url}/actuator/health")" || fail "actuator health unreachable"
grep -q '"status":"UP"' <<<"$health" || fail "actuator health is not UP: $health"
curl --silent --fail "${consumer_url}/actuator/prometheus" | grep -q '^application_ready_time_seconds' \
  || fail "prometheus endpoint lacks application_ready_time_seconds"

memory="$(docker stats --no-stream --format '{{.MemUsage}}' "$consumer")"
size_bytes="$(docker image inspect --format '{{.Size}}' "$image")"
echo "Smoke test passed for ${image}"
echo "  startup (Spring 'Started EmailApplication'): ${startup}s"
echo "  image size: $((size_bytes / 1024 / 1024)) MiB"
echo "  memory after traffic (docker stats): ${memory}"
