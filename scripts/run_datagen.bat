@echo off
REM Script to run data generation for Fishtastic mod
cd /d "%~dp0\.."
echo Running Fabric Data Generator...
call gradlew.bat :fabric:runDatagen
echo.
echo Syncing datapack registry JSON to common/src/main/resources...
python scripts\copy_datapack_registries.py
echo.
echo Data generation complete!
pause

