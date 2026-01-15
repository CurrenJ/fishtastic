@echo off
REM Script to run data generation for Fishtastic mod
cd /d "%~dp0"
echo Running Fabric Data Generator...
gradlew.bat :fabric:runDatagen
echo.
echo Data generation complete!
echo Generated models can be found in: fabric\src\main\generated\resources\assets\fishtastic\models\block\
pause

