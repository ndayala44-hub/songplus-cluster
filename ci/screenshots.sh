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
# Aurora cluster over one auto-drive cycle (starts when the app starts)
adb shell am force-stop com.songplus.cluster
adb shell am start -n com.songplus.cluster/.MainActivity --es screen cluster --ez seed_demo true
sleep 2;  adb exec-out screencap -p > shots/01a_aurora_intro.png
sleep 6;  adb exec-out screencap -p > shots/01b_aurora_pulling_away.png
sleep 11; adb exec-out screencap -p > shots/01c_aurora_signal_right.png
sleep 14; adb exec-out screencap -p > shots/01d_aurora_cruising.png
sleep 10; adb exec-out screencap -p > shots/01e_aurora_braking.png
sleep 6;  adb exec-out screencap -p > shots/01f_aurora_parked.png
# light theme cluster (debug-only theme extra)
adb shell am force-stop com.songplus.cluster
adb shell am start -n com.songplus.cluster/.MainActivity --es screen cluster --es theme light
sleep 12; adb exec-out screencap -p > shots/08a_aurora_light_pulling_away.png
sleep 14; adb exec-out screencap -p > shots/08b_aurora_light_cruising.png
sleep 11; adb exec-out screencap -p > shots/08c_aurora_light_braking.png
adb shell am force-stop com.songplus.cluster
adb shell am start -n com.songplus.cluster/.MainActivity --es screen cluster --es theme dark
sleep 3
open 02_trips --es screen trips --ez seed_demo true
open 03_designer --es screen designer
open 04_settings_general --es screen settings --ei cat 0
open 05_settings_appearance --es screen settings --ei cat 1
open 06_settings_cluster --es screen settings --ei cat 2
open 07_settings_vehicle --es screen settings --ei cat 3
adb logcat -d -s AndroidRuntime:E | head -40
