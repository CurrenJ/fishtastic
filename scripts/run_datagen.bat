@echo off
REM Fabric datagen safely merges common resources on success.
cd /d "%~dp0\.."
call gradlew.bat :fabric:runDatagen %*
exit /b %errorlevel%
