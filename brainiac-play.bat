@echo off
cd /d "%~dp0"
python -c "import tivars, PIL" 2>nul
if errorlevel 1 (
  echo Installing what the Brainiac simulator needs, once...
  python -m pip install -r brainiac\requirements.txt
)
echo Brainiac on a pretend TI-84 Plus CE. Type quit to stop.
python -m brainiac.play
pause
