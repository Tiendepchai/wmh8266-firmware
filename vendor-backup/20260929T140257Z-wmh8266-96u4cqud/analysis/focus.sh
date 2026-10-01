#!/bin/sh
# Offline Ghidra query; no device or network I/O.
set -eu
umask 077
A=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home
exec /opt/homebrew/opt/ghidra/libexec/support/analyzeHeadless "$A/projects" WMH8266 -process app.hex -noanalysis -scriptPath "$A" -postScript ExportFocus.java "$A" "$@"
