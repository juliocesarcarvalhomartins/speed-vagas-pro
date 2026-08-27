@echo off
cd /d "%~dp0app"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0app\bin\launcher.ps1"
