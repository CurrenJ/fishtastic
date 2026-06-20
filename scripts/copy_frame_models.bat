@echo off
REM Script to copy generated fish tank frame and sand models to resource folders
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
    echo ERROR: Generated frame models not found!
    echo Please run the data generator first using run_datagen.bat
    echo.
    pause
    exit /b 1
)

if not exist "%SOURCE%\fishtankbase\fish_tank_sand_0.json" (
    echo ERROR: Generated sand models not found!
    echo Please run the data generator first using run_datagen.bat
    echo.
    pause
    exit /b 1
)

echo Found generated models.
echo.

REM Count the frame files
for /f %%A in ('dir /b "%SOURCE%\fishtankbase\fish_tank_frame_*.json" 2^>nul ^| find /c /v ""') do set FRAME_COUNT=%%A
echo Found %FRAME_COUNT% fish tank frame model(s)
echo Expected: 64 models
echo.

if not %FRAME_COUNT%==64 (
    echo WARNING: Expected 64 frame models but found %FRAME_COUNT%
    echo Continue anyway? (Y/N)
    choice /c YN /n
    if errorlevel 2 exit /b 1
)

REM Count the sand files
for /f %%A in ('dir /b "%SOURCE%\fishtankbase\fish_tank_sand_*.json" 2^>nul ^| find /c /v ""') do set SAND_COUNT=%%A
echo Found %SAND_COUNT% fish tank sand model(s)
echo Expected: 64 models
echo.

if not %SAND_COUNT%==64 (
    echo WARNING: Expected 64 sand models but found %SAND_COUNT%
    echo Continue anyway? (Y/N)
    choice /c YN /n
    if errorlevel 2 exit /b 1
)

REM Create destination directories if they don't exist
if not exist "%DEST_FABRIC%\fishtankbase" mkdir "%DEST_FABRIC%\fishtankbase"
if not exist "%DEST_NEOFORGE%\fishtankbase" mkdir "%DEST_NEOFORGE%\fishtankbase"

echo Copying frame models to Fabric resources...
xcopy /Y "%SOURCE%\fishtankbase\fish_tank_frame_*.json" "%DEST_FABRIC%\fishtankbase\"
if errorlevel 1 (
    echo ERROR: Failed to copy frame models to Fabric resources
    pause
    exit /b 1
)
echo Done!

echo.
echo Copying sand models to Fabric resources...
xcopy /Y "%SOURCE%\fishtankbase\fish_tank_sand_*.json" "%DEST_FABRIC%\fishtankbase\"
if errorlevel 1 (
    echo ERROR: Failed to copy sand models to Fabric resources
    pause
    exit /b 1
)
echo Done!

echo.
echo Copying frame models to NeoForge resources...
xcopy /Y "%SOURCE%\fishtankbase\fish_tank_frame_*.json" "%DEST_NEOFORGE%\fishtankbase\"
if errorlevel 1 (
    echo ERROR: Failed to copy frame models to NeoForge resources
    pause
    exit /b 1
)
echo Done!

echo.
echo Copying sand models to NeoForge resources...
xcopy /Y "%SOURCE%\fishtankbase\fish_tank_sand_*.json" "%DEST_NEOFORGE%\fishtankbase\"
if errorlevel 1 (
    echo ERROR: Failed to copy sand models to NeoForge resources
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
echo   - %DEST_FABRIC%\fishtankbase
echo   - %DEST_NEOFORGE%\fishtankbase
echo.

REM Optional: Show file count in each destination
for /f %%A in ('dir /b "%DEST_FABRIC%\fishtankbase\fish_tank_frame_*.json" 2^>nul ^| find /c /v ""') do set FABRIC_FRAME_COUNT=%%A
for /f %%A in ('dir /b "%DEST_NEOFORGE%\fishtankbase\fish_tank_frame_*.json" 2^>nul ^| find /c /v ""') do set NEOFORGE_FRAME_COUNT=%%A
for /f %%A in ('dir /b "%DEST_FABRIC%\fishtankbase\fish_tank_sand_*.json" 2^>nul ^| find /c /v ""') do set FABRIC_SAND_COUNT=%%A
for /f %%A in ('dir /b "%DEST_NEOFORGE%\fishtankbase\fish_tank_sand_*.json" 2^>nul ^| find /c /v ""') do set NEOFORGE_SAND_COUNT=%%A

echo Fabric:   %FABRIC_FRAME_COUNT% frame models, %FABRIC_SAND_COUNT% sand models
echo NeoForge: %NEOFORGE_FRAME_COUNT% frame models, %NEOFORGE_SAND_COUNT% sand models
echo.

pause

