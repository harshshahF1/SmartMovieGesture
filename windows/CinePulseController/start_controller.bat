@echo off
title CinePulse Controller
cd /d "%~dp0"
netsh advfirewall firewall add rule name="CinePulse Controller TCP 8765" dir=in action=allow protocol=TCP localport=8765 profile=any >nul 2>&1
netsh advfirewall firewall add rule name="CinePulse Controller UDP 8766" dir=in action=allow protocol=UDP localport=8766 profile=any >nul 2>&1
python server.py
pause
