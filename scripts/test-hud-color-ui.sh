#!/usr/bin/env bash
set -euo pipefail
project=$1
version=$2
api=$3
mkdir -p "$project/run"
cat > "$project/run/options.txt" <<'OPTIONS'
guiScale:2
overrideWidth:1024
overrideHeight:768
fullscreen:false
lang:en_us
OPTIONS
log="$RUNNER_TEMP/quartz-color.log"
setsid gradle -p "$project" "-Pminecraft_version=$version" "-Pfabric_api_version=$api" runClient > "$log" 2>&1 &
game=$!
trap 'kill -TERM -- "-$game" 2>/dev/null || true' EXIT
trap 'tail -100 "$log"' ERR
window=""
for i in {1..75}; do
  if grep -q 'Created: .*minecraft:textures/atlas/gui.png-atlas' "$log"; then
    window=$(xdotool search --onlyvisible --name 'Minecraft' | tail -1 || true)
    test -z "$window" || break
  fi
  kill -0 "$game"
  sleep 3
done
test -n "$window"
echo "Minecraft window: $window"
xdotool windowsize "$window" 1024 768
xdotool windowfocus "$window"
sleep 5
xdotool getwindowgeometry "$window"
click() {
  xdotool mousemove --window "$window" $(($1 * 2)) $(($2 * 2)) click 1
  sleep 0.6
}
# Main menu > Mods > HUD layout > Appearance > Background color.
click 104 44
click 53 96
click 250 15
click 84 128
click 105 259
xdotool key ctrl+a
xdotool type '#8066CCFF'
click 405 283
file="$project/run/config/quartz-client.json"
check_colors() {
  python3 - "$file" "$1" "$2" <<'PYTHON'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as file:
    fps = json.load(file)["modules"]["FPS"]
for field, expected in zip(("bg", "fg"), sys.argv[2:]):
    obtained = fps[field] & 0xffffffff
    assert obtained == int(expected, 16), f"{field} got {obtained:08X} expected {expected}"
PYTHON
}
check_colors 8066CCFF FFF3F5FF
click 245 128
click 126 146
click 158 219
click 177 241
xdotool key Escape
sleep 0.6
click 245 128
click 405 283
check_colors 8066CCFF FFF3F5FF
if grep -qE 'Could not render QuartzColorPickerScreen|Quartz menu action failed' "$log"; then
  tail -100 "$log"
  exit 1
fi
echo "HUD color picker saved the new background and canceled the temporary text color."
