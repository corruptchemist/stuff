@echo off
cd /d "%~dp0"
echo ============================================================
echo   Flip 7 recon - read-only inspection of a live BGA table
echo ============================================================
echo.
python -m pip install -q -r flip7\requirements.txt
if errorlevel 1 goto fail
python -m flip7.recon --capture 120
if errorlevel 1 goto fail
echo.
echo Done. Send me flip7-recon.json
pause
exit /b 0
:fail
echo.
echo Something failed above - copy the error text and send it over.
pause
