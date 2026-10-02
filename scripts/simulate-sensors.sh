#!/usr/bin/env bash
set -euo pipefail

HOST="${HOST:-localhost}"
TEMPERATURE_PORT="${TEMPERATURE_PORT:-3344}"
HUMIDITY_PORT="${HUMIDITY_PORT:-3355}"

send() {
  echo "-> udp://${HOST}:$1  $2"
  printf '%s\n' "$2" | nc -u -w1 "$HOST" "$1"
}

send "$TEMPERATURE_PORT" "sensor_id=t1; value=30"
send "$HUMIDITY_PORT" "sensor_id=h1; value=40"
send "$TEMPERATURE_PORT" "sensor_id=t1; value=36"
send "$TEMPERATURE_PORT" "sensor_id=t1; value=38"
send "$HUMIDITY_PORT" "sensor_id=h1; value=55"
send "$TEMPERATURE_PORT" "sensor_id=t1; value=33"
send "$HUMIDITY_PORT" "sensor_id=h1; value=45"
