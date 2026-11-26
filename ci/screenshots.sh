#!/bin/bash
# Runs inside the emulator: installs the APK, opens each screen and saves a screenshot.
set -x
adb install -r app-debug.apk
adb shell wm size 1920x720
adb shell wm density 213
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
mkdir -p shots
shot() { sleep 7; adb exec-out screencap -p > "shots/$1.png"; }
open() { adb shell am force-stop com.songplus.cluster; adb shell am start -n com.songplus.cluster/.MainActivity "${@:2}"; shot "$1"; }
open 01_cluster_simulation --es screen cluster --ez seed_demo true
open 02_trips --es screen trips --ez seed_demo true
open 03_designer --es screen designer
open 04_settings_general --es screen settings --ei cat 0
open 05_settings_appearance --es screen settings --ei cat 1
open 06_settings_cluster --es screen settings --ei cat 2
open 07_settings_vehicle --es screen settings --ei cat 3
adb logcat -d -s AndroidRuntime:E | head -40
