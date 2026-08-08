#!/usr/bin/env bash
# Builds the web WAR and brings the Open Liberty server up (https://localhost:9443).
#
# IMPORTANT: `mvn clean package` wipes web/target/liberty (the installed runtime + the
# auto-generated keystore). Run this script after any clean build: it (re)creates the
# server config, installs the features declared in server.xml (first time only), starts
# the server and deploys the WAR into dropins.
set -euo pipefail

cd "$(dirname "$0")/.."

WEB_TARGET=web/target/liberty
WEB_PROFILE_MF="$WEB_TARGET/wlp/lib/features/io.openliberty.webProfile-11.0.mf"

# Build the WAR without `clean`, so an existing Liberty install is preserved.
# `install` also publishes the sibling module jars to the local repo, which the
# fresh-install path below needs when it runs `liberty:create` on `web` alone.
mvn -q -pl web -am install -DskipTests

# Fresh install? Create the server config and install the declared features.
if [ ! -f "$WEB_PROFILE_MF" ]; then
  echo "Liberty runtime missing features; installing them (one-time download)..."
  mvn -q -pl web liberty:create
  "$WEB_TARGET/wlp/bin/featureUtility" installServerFeatures defaultServer --acceptLicense
fi

# Start if not running (ignore "server already started" from a previous run).
mvn -q -pl web liberty:start || true

# Deploy (hot-redeploys if the server was already running).
cp web/target/web.war "$WEB_TARGET/wlp/usr/servers/defaultServer/dropins/web.war"

for i in $(seq 1 60); do
  if [ "$(curl -sk -o /dev/null -w '%{http_code}' https://localhost:9443/web/)" = "200" ]; then
    echo "App is up: https://localhost:9443/web/"
    exit 0
  fi
  sleep 2
done

echo "Timed out waiting for the app; check $WEB_TARGET/wlp/usr/servers/defaultServer/logs/messages.log" >&2
exit 1
