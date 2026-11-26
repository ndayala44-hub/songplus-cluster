#!/bin/bash
# Runs inside the emulator: installs the APK, opens each screen and saves a screenshot.
set -x
adb install -r app-debug.apk
adb shell wm size 1920x720
adb shell wm density 213
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb shell wm user-rotation lock 1
adb shell settings put secure immersive_mode_confirmations confirmed
sleep 5
adb shell wm size; adb shell wm density
mkdir -p shots
shot() { sleep 7; adb exec-out screencap -p > "shots/$1.png"; }
open() { adb shell am force-stop com.songplus.cluster; adb shell am start -n com.songplus.cluster/.MainActivity "${@:2}"; shot "$1"; }
# Aurora over the auto-drive cycle (dark, Normal mode)
adb shell am force-stop com.songplus.cluster
adb shell am start -n com.songplus.cluster/.MainActivity --es screen cluster --es theme dark --ez seed_demo true
sleep 2;  adb exec-out screencap -p > shots/01a_aurora_intro.png
sleep 6;  adb exec-out screencap -p > shots/01b_aurora_pulling_away.png
sleep 25; adb exec-out screencap -p > shots/01d_aurora_cruising.png
open 02_trips --es screen trips --ez seed_demo true --es theme dark
open 03_designer --es screen designer
open 04_settings_general --es screen settings --ei cat 0
open 05_settings_appearance --es screen settings --ei cat 1
open 06_settings_cluster --es screen settings --ei cat 2
open 07_settings_vehicle --es screen settings --ei cat 3
adb shell input swipe 960 600 960 100 400; sleep 1; adb shell input swipe 960 600 960 100 400; sleep 1
adb exec-out screencap -p > shots/07b_settings_vehicle_music_camera.png

# ---- drive modes x themes: red must appear only in Sport ----
adb shell pm grant com.songplus.cluster android.permission.CAMERA || true
adb shell cmd notification allow_listener com.songplus.cluster/com.songplus.cluster.media.MediaListenerService || true
for th in dark light; do
  for m in eco normal sport; do
    adb shell am force-stop com.songplus.cluster
    adb shell am start -n com.songplus.cluster/.MainActivity --es screen cluster --es theme $th --es mode $m --es gear D --ef speed 70
    sleep 12; adb exec-out screencap -p > shots/10_mode_${m}_${th}.png
  done
done

# ---- music: a real MediaSession published by the debug fixture, read through MediaSessionManager ----
for th in dark light; do
  adb shell am force-stop com.songplus.cluster
  adb shell am start -n com.songplus.cluster/.MainActivity --es screen cluster --es theme $th --es mode normal --es gear D --ef speed 50 --ez test_media true
  sleep 12; adb exec-out screencap -p > shots/20_music_${th}.png
done

# ---- reverse: camera tile replaces the car (emulator virtual camera), then fallback without permission ----
for th in dark light; do
  adb shell am force-stop com.songplus.cluster
  adb shell am start -n com.songplus.cluster/.MainActivity --es screen cluster --es theme $th --es mode normal --es gear R --ef speed 0
  sleep 14; adb exec-out screencap -p > shots/30_reverse_${th}.png
done
adb shell pm revoke com.songplus.cluster android.permission.CAMERA || true
adb shell am force-stop com.songplus.cluster
adb shell am start -n com.songplus.cluster/.MainActivity --es screen cluster --es theme dark --es mode normal --es gear R --ef speed 0
sleep 10; adb exec-out screencap -p > shots/31_reverse_fallback_no_permission.png
adb shell am force-stop com.songplus.cluster
adb shell am start -n com.songplus.cluster/.MainActivity --es screen cluster --es theme dark --es mode sport --es gear R --ef speed 0
sleep 10; adb exec-out screencap -p > shots/32_reverse_sport_fallback.png
adb logcat -d -s AndroidRuntime:E | head -40
adb logcat -d | grep -i "reverse-cam\|SongPlus\|MediaSession" | head -30
