@echo off
cd /d "%~dp0"
git pull --ff-only 2>nul
python -m pip install -q -r flip7\requirements.txt
echo === TABS AND FRAMES ===
python -m flip7.app --diagnose
echo === TRACKER STATE ===
python -m flip7.app --dump
echo.
pause
