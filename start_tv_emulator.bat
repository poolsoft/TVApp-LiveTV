@echo off
title Google TV Emulator (TVApp_GoogleTV_API31)
echo ===================================================
echo   Google TV Emulator Baslatiliyor...
echo ===================================================
set "ANDROID_SDK_ROOT=D:\Android\Sdk"
set "ADB_VENDOR_KEYS=C:\Users\pools\.android\adbkey"
start "" "%ANDROID_SDK_ROOT%\emulator\emulator.exe" -avd TVApp_GoogleTV_API31 -no-audio -no-metrics
echo Emulasyon penceresi acildi.
