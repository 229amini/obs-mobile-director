@echo off
setlocal

net session >nul 2>&1
if %errorlevel% neq 0 (
  echo Requesting administrator permission...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

netsh advfirewall firewall show rule name="OBS Mobile SRT 9001-9002" >nul 2>&1
if %errorlevel% equ 0 (
  echo Firewall rule already exists.
) else (
  netsh advfirewall firewall add rule name="OBS Mobile SRT 9001-9002" dir=in action=allow protocol=UDP localport=9001-9002 profile=private
)

echo.
echo Done. You can close this window.
pause
