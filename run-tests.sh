#!/bin/bash
# Run all unit tests via Gradle (pure-JVM + Robolectric).
set -e

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

./gradlew testDebugUnitTest "$@"
