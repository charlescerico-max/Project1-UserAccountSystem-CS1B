@echo off
title Flintlock API — port 8080
cd /d "%~dp0"

echo.
echo Starting Java HTTP API...
echo Keep this window OPEN while you use the website (Live Server on port 5500).
echo API URL: http://localhost:8080
echo.

mvn -q exec:java

echo.
if errorlevel 1 (
    echo --- Failed or stopped ---
    echo If you see "Address already in use", another program is using port 8080.
    echo Find it:  netstat -ano ^| findstr ":8080"
    echo Then stop that Java window, or:  taskkill /PID ^<pid^> /F
    echo After code changes, run "mvn compile" before starting again.
)
pause
