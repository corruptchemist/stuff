@echo off
cd /d "%~dp0"
set "SITE=%~1"
if "%SITE%"=="" set "SITE=https://wordleunlimited.org/"
echo Opening %SITE% - a browser window will appear and play by itself.
echo Watch the stats panel in the top right corner.
echo.
python -m wordlebot.browser "%SITE%" --games 5 --no-headless
echo.
pause
