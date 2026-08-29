@echo off
cd /d "%~dp0"
echo Opening the local Wordle for you to play.
echo Leave this window open while you play. Press Ctrl-C here to stop.
echo.
python -m wordlebot.server
