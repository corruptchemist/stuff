@echo off
cd /d "%~dp0"
echo ============================================================
echo   Flip 7 tracker - reads your table, never plays it
echo ============================================================
echo.
python -m pip install -q -r flip7\requirements.txt
if errorlevel 1 goto fail
python -m flip7.app %*
goto :eof
:fail
echo.
echo Setup failed above - copy the error text and send it over.
pause
