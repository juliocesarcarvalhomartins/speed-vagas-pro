@echo off
setlocal
cd /d "%~dp0"
if not exist build mkdir build
if exist build\classes rmdir /s /q build\classes
mkdir build\classes

where javac >nul 2>nul
if errorlevel 1 (
  echo [ERRO] JDK 21 nao encontrado.
  echo Instale Java JDK 21 e tente novamente.
  pause
  exit /b 1
)

javac --add-modules jdk.httpserver -encoding UTF-8 -d build\classes src\main\java\*.java src\test\java\*.java
if errorlevel 1 (
  echo [ERRO] Compilacao falhou.
  pause
  exit /b 1
)

jar --create --file build\speed-vagas.jar -C build\classes .
echo [OK] build\speed-vagas.jar criado.
pause
