@echo off
setlocal

cd /d %~dp0

echo [Barrier Android Client] Building debug APK...
gradle assembleDebug
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo APK generated at:
echo %~dp0app\build\outputs\apk\debug\app-debug.apk
exit /b 0
