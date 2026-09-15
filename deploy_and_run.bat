@echo off
title TVApp Deploy & Run
echo ===================================================
echo   TVApp Emulatore Yukleniyor ve Baslatiliyor...
echo ===================================================
set "ADB=D:\Android\Sdk\platform-tools\adb.exe"
echo Cihazin hazir olmasi bekleniyor...
%ADB% wait-for-device
echo APK yukleniyor...
%ADB% install -r "D:\Projects\TVWorkSpace\TVApp\app\build\outputs\apk\local\debug\app-local-debug.apk"
echo TVApp baslatiliyor...
%ADB% shell am start -n com.tvapp.livetv/.MainActivity
echo Islem tamamlandi!
pause
