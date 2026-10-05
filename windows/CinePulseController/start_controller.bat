@echo off
title CinePulse Controller
cd /d "%~dp0"

set /p CINEPULSE_RELAY_URL=Enter CinePulse HTTPS relay URL: 
set /p CINEPULSE_PAIRING_CODE=Enter pairing code from phone: 

set CINEPULSE_RELAY_URL=%CINEPULSE_RELAY_URL%
set CINEPULSE_PAIRING_CODE=%CINEPULSE_PAIRING_CODE%

python server.py
pause
