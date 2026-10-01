#!/bin/sh
# Offline import. Refuses to replace an existing project. No device access.
set -eu
umask 077
A=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
P="$A/reproduced-projects"
if [ -e "$P/WMH8266.gpr" ] || [ -d "$P/WMH8266.rep" ]; then
  printf '%s\n' 'Existing reproduced project; refusing to overwrite.' >&2
  exit 1
fi
python3 "$A/map_firmware.py" > "$A/reproduce-map.log"
mkdir -p "$P" "$A/reproduced-focus" "$A/reproduced-boot"
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home
H=/opt/homebrew/opt/ghidra/libexec/support/analyzeHeadless
"$H" "$P" WMH8266 -import "$A/app.hex" -loader IntelHexLoader -processor Xtensa:LE:32:default -cspec default -scriptPath "$A" -preScript PrepareXtensa.java -postScript ExportFocus.java "$A/reproduced-focus" 40100004 -analysisTimeoutPerFile 240 > "$A/reproduce-import.log" 2>&1
"$H" "$P" WMH8266 -process app.hex -noanalysis -scriptPath "$A" -postScript AnnotateEvidence.java -postScript ValidateImport.java "$A" -postScript ExportFocus.java "$A/reproduced-focus" 4022d4a8 40230794 4022e004 40235d40 40235c28 40106698 4022d914 4022fae4 4022fef4 4022e4fc 40230f70 40220dd8 4022f640 402455fc 402459d0 > "$A/reproduce-focus.log" 2>&1
"$H" "$P" WMH8266 -import "$A/boot.hex" -loader IntelHexLoader -processor Xtensa:LE:32:default -cspec default -scriptPath "$A" -preScript PrepareBoot.java -postScript ExportFocus.java "$A/reproduced-boot" 40100420 > "$A/reproduce-boot.log" 2>&1
# Ghidra may explicitly create project files with broader mode bits despite umask.
python3 - "$A" <<'PY'
from pathlib import Path
import os
import sys
root = Path(sys.argv[1])
for directory, dirs, files in os.walk(root):
    os.chmod(directory, 0o700)
    for name in files:
        os.chmod(Path(directory) / name, 0o600)
assert all((p.stat().st_mode & 0o077) == 0 for p in [root, *root.rglob('*')])
PY
