@echo off
REM Jump to this script folder, so it works from any directory or a double-click.
cd /d "%~dp0"
echo ============================================================
echo   Flip 7 tracker - reads your table, never plays it
echo ============================================================
echo.
echo Updating...
git pull --ff-only 2>nul
if errorlevel 1 echo   (skipped - no git, or local changes; carrying on)
echo.
python -m pip install -q -r flip7\requirements.txt
if errorlevel 1 goto fail
python -m flip7.app %*
goto :eof
:fail
echo.
echo Setup failed above - copy the error text and send it over.
pause
