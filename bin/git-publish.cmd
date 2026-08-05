@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%..\lib\git-publish.jar"

if not exist "%JAR%" (
  echo [ERROR] JAR not found: "%JAR%" 1>&2
  exit /b 1
)

java -jar "%JAR%" %*
exit /b %ERRORLEVEL%
