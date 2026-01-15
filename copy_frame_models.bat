@echo off
REM Script to copy generated fish tank frame models to resource folders
cd /d "%~dp0"

echo ========================================
echo Fish Tank Model Copy Script
echo ========================================
echo.

SET SOURCE=fabric\src\main\generated\resources\assets\fishtastic\models\block
SET DEST_FABRIC=fabric\src\main\resources\assets\fishtastic\models\block
SET DEST_NEOFORGE=neoforge\src\main\resources\assets\fishtastic\models\block

echo Checking if generated models exist...
if not exist "%SOURCE%\fish_tank_frame_0.json" (
    echo ERROR: Generated models not found!
    echo Please run the data generator first using run_datagen.bat
    echo.
    pause
    exit /b 1
)

echo Found generated models.
echo.

REM Count the files
for /f %%A in ('dir /b "%SOURCE%\fish_tank_frame_*.json" 2^>nul ^| find /c /v ""') do set COUNT=%%A
echo Found %COUNT% fish tank frame model(s)
echo Expected: 64 models
echo.

if not %COUNT%==64 (
    echo WARNING: Expected 64 models but found %COUNT%
    echo Continue anyway? (Y/N)
    choice /c YN /n
    if errorlevel 2 exit /b 1
)

REM Create destination directories if they don't exist
if not exist "%DEST_FABRIC%" mkdir "%DEST_FABRIC%"
if not exist "%DEST_NEOFORGE%" mkdir "%DEST_NEOFORGE%"

echo Copying models to Fabric resources...
xcopy /Y "%SOURCE%\fish_tank_frame_*.json" "%DEST_FABRIC%\"
if errorlevel 1 (
    echo ERROR: Failed to copy to Fabric resources
    pause
    exit /b 1
)
echo Done!

echo.
echo Copying models to NeoForge resources...
xcopy /Y "%SOURCE%\fish_tank_frame_*.json" "%DEST_NEOFORGE%\"
if errorlevel 1 (
    echo ERROR: Failed to copy to NeoForge resources
    pause
    exit /b 1
)
echo Done!

echo.
echo ========================================
echo SUCCESS! Models copied to both mod loaders.
echo ========================================
echo.
echo Files copied to:
echo   - %DEST_FABRIC%
echo   - %DEST_NEOFORGE%
echo.

REM Optional: Show file count in each destination
for /f %%A in ('dir /b "%DEST_FABRIC%\fish_tank_frame_*.json" 2^>nul ^| find /c /v ""') do set FABRIC_COUNT=%%A
for /f %%A in ('dir /b "%DEST_NEOFORGE%\fish_tank_frame_*.json" 2^>nul ^| find /c /v ""') do set NEOFORGE_COUNT=%%A

echo Fabric:   %FABRIC_COUNT% frame models
echo NeoForge: %NEOFORGE_COUNT% frame models
echo.

pause

