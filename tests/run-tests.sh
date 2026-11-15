#!/usr/bin/env bash
# The test suite: the builder's JVM tests (:core), against fixtures made from rootiest-icons' own output
# (scripts/make-fixtures.py), then a debug build of the app. No device needed. CI runs it.
# Needs the Android SDK: ANDROID_HOME, or sdk.dir in local.properties.
set -eu
cd "$(dirname "$0")/.."
./gradlew --no-daemon :core:test :app:assembleDebug
