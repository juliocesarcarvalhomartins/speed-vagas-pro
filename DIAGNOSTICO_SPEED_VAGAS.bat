@echo off
chcp 65001 >nul
title SPEED VAGAS PRO - Diagnostico
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0launcher\bootstrap.ps1"
echo.
echo Consulte launcher\launcher.log
pause
