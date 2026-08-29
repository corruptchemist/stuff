@echo off
cd /d "%~dp0"
echo ==========================================
echo    wordlebot setup  (run this once)
echo ==========================================
echo.
where python >/dev/null 2>&1
if errorlevel 1 (
  echo ERROR: Python is not on your PATH.
  echo Install it from https://python.org/downloads and tick
  echo "Add python.exe to PATH" during install, then run this again.
  pause
  exit /b 1
)
echo [1/3] Installing Python packages...
python -m pip install --upgrade -r requirements.txt
if errorlevel 1 goto fail
echo.
echo [2/3] Downloading the browser Playwright drives (~150 MB, once)...
python -m playwright install chromium
if errorlevel 1 goto fail
echo.
echo [3/3] Building the guess/answer table (about 30 seconds, once)...
python -c "from wordlebot.solver import Solver; Solver()"
if errorlevel 1 goto fail
echo.
echo ==========================================
echo    Done. Now double-click one of these:
echo      play-browser.bat  - it plays a site by itself
echo      play-manual.bat   - it tells you what to type
echo ==========================================
pause
exit /b 0

:fail
echo.
echo Setup failed above. Copy the error text and send it over.
pause
exit /b 1
