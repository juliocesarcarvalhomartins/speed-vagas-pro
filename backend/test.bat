@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1

java -cp build\classes LogicSelfTest
if errorlevel 1 exit /b 1

java -cp build\classes ProviderParsingTest
if errorlevel 1 exit /b 1

echo [OK] Testes de regras e providers passaram.
pause
