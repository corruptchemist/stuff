@echo off
setlocal
REM Jump to this script folder, so it works from any directory or a double-click.
cd /d "%~dp0"
echo ============================================================
echo   Flip 7 tracker - reads your table, never plays it
echo ============================================================
echo.
where python >nul 2>&1
if errorlevel 1 (
  echo ERROR: Python is not on your PATH.
  echo Install from https://python.org/downloads and tick "Add python.exe to PATH".
  goto done
)
echo Updating...
git pull --ff-only
if errorlevel 1 echo   (update skipped - carrying on with the version you have)
echo.
echo Installing dependencies...
python -m pip install -q -r flip7\requirements.txt
if errorlevel 1 (
  echo ERROR: could not install dependencies - see above.
  goto done
)
echo.
python -m flip7.app %*
if errorlevel 1 (
  echo.
  echo ============================================================
  echo  The tracker stopped with an error. The message is above.
  echo  Copy it and send it over.
  echo ============================================================
)
:done
echo.
pause
