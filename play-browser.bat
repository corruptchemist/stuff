@echo off
cd /d "%~dp0"
set "SITE=%~1"
if "%SITE%"=="" set "SITE=https://wordleunlimited.org/"
echo Playing %SITE%
echo No window will open and your keyboard is not touched - carry on using the PC.
echo.
python -m wordlebot.browser "%SITE%" --games 5
echo.
pause
