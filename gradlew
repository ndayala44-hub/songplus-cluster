#!/usr/bin/env sh
# Minimal Gradle wrapper launcher (Unix). Uses gradle/wrapper/gradle-wrapper.jar.
die() { echo "ERROR: $*" >&2; exit 1; }

APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
APP_BASE_NAME=$(basename "$0")

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
elif [ -n "$JAVA_HOME" ]; then
    die "JAVA_HOME is set to an invalid directory: $JAVA_HOME"
else
    command -v java >/dev/null 2>&1 || die "JAVA_HOME is not set and no 'java' command was found in PATH."
    JAVACMD=java
fi

exec "$JAVACMD" $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
