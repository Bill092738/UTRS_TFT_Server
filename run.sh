#!/usr/bin/env sh
# Build and start the server (TCP :23363 + web http://localhost:8080/).
#   ./run.sh [server options]   e.g. ./run.sh --host 0.0.0.0
#   ./run.sh test               run the self-test
set -e
cd "$(dirname "$0")"
rm -rf server/out
javac -encoding UTF-8 -d server/out server/src/utrs/*.java server/test/utrs/*.java
if [ "$1" = "test" ]; then
  exec java -cp server/out utrs.SelfTest
fi
exec java -cp server/out utrs.App "$@"
