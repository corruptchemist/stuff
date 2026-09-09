@echo off
cd /d "%~dp0"
git pull --ff-only 2>nul
python -m pip install -q -r flip7\requirements.txt
python -m flip7.app --diagnose
echo.
pause
