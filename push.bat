@echo off
cd /d "%~dp0"
set GIT="C:\Users\Matt\AppData\Local\GitHubDesktop\app-3.6.5\resources\app\git\cmd\git.exe"

echo Checking for changes...
%GIT% add .

set MSG=Update from Claude
set /p MSG="Commit message (press Enter to use default): "

%GIT% commit -m "%MSG%"
%GIT% push

echo.
echo Done. Check GitHub Actions in a few minutes for the new build.
pause
