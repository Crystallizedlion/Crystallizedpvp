@echo off
cd /d "%~dp0plugin"
call mvn -q clean package || exit /b 1
copy /Y target\CrystallizedPvP.jar ..\server\plugins\CrystallizedPvP.jar
echo Built -^> server\plugins\CrystallizedPvP.jar
pause
