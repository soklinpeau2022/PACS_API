@echo off
set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=local"
powershell -ExecutionPolicy Bypass -File "%~dp0db-admin.ps1" -Action migrate -Target "%TARGET%" -Build
