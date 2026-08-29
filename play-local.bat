@echo off
cd /d "%~dp0"
echo Starting the local Wordle and letting the bot play it.
echo A browser window will open - watch the stats panel on the right.
echo.
python -m wordlebot.browser --local --games 8 --no-headless
echo.
pause
