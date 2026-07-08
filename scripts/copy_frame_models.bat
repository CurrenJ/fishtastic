@echo off
REM Script to copy generated fish tank frame/sand/glass models to the NeoForge resource folder.
REM Fabric doesn't need this: its build.gradle merges src/main/generated directly into
REM resources at build time (see fabric/build.gradle processResources). NeoForge has no such
REM step, so its checked-in resources must be kept in sync manually after datagen runs.
cd /d "%~dp0\.."

echo ========================================
echo Fish Tank Model Copy Script
echo ========================================
echo.

SET SOURCE=fabric\src\main\generated\assets\fishtastic\models\block\fishtankbase
SET DEST_NEOFORGE=neoforge\src\main\resources\assets\fishtastic\models\block\fishtankbase

echo Checking if generated models exist...
if not exist "%SOURCE%\fish_tank_frame_0.json" (
    echo ERROR: Generated frame models not found!
    echo Please run the data generator first using run_datagen.bat
    echo.
    pause
    exit /b 1
)

if not exist "%SOURCE%\fish_tank_sand_0.json" (
    echo ERROR: Generated sand models not found!
    echo Please run the data generator first using run_datagen.bat
    echo.
    pause
    exit /b 1
)

if not exist "%SOURCE%\fish_tank_glass_0.json" (
    echo ERROR: Generated glass models not found!
    echo Please run the data generator first using run_datagen.bat
    echo.
    pause
    exit /b 1
)

echo Found generated models.
echo.

REM Count the frame files
for /f %%A in ('dir /b "%SOURCE%\fish_tank_frame_*.json" 2^>nul ^| find /c /v ""') do set FRAME_COUNT=%%A
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
for /f %%A in ('dir /b "%SOURCE%\fish_tank_sand_*.json" 2^>nul ^| find /c /v ""') do set SAND_COUNT=%%A
echo Found %SAND_COUNT% fish tank sand model(s)
echo Expected: 64 models
echo.

if not %SAND_COUNT%==64 (
    echo WARNING: Expected 64 sand models but found %SAND_COUNT%
    echo Continue anyway? (Y/N)
    choice /c YN /n
    if errorlevel 2 exit /b 1
)

REM Count the glass files
for /f %%A in ('dir /b "%SOURCE%\fish_tank_glass_*.json" 2^>nul ^| find /c /v ""') do set GLASS_COUNT=%%A
echo Found %GLASS_COUNT% fish tank glass model(s)
echo Expected: 64 models
echo.

if not %GLASS_COUNT%==64 (
    echo WARNING: Expected 64 glass models but found %GLASS_COUNT%
    echo Continue anyway? (Y/N)
    choice /c YN /n
    if errorlevel 2 exit /b 1
)

REM Create destination directory if it doesn't exist
if not exist "%DEST_NEOFORGE%" mkdir "%DEST_NEOFORGE%"

echo Copying frame models to NeoForge resources...
xcopy /Y "%SOURCE%\fish_tank_frame_*.json" "%DEST_NEOFORGE%\"
if errorlevel 1 (
    echo ERROR: Failed to copy frame models to NeoForge resources
    pause
    exit /b 1
)
echo Done!

echo.
echo Copying sand models to NeoForge resources...
xcopy /Y "%SOURCE%\fish_tank_sand_*.json" "%DEST_NEOFORGE%\"
if errorlevel 1 (
    echo ERROR: Failed to copy sand models to NeoForge resources
    pause
    exit /b 1
)
echo Done!

echo.
echo Copying glass models to NeoForge resources...
xcopy /Y "%SOURCE%\fish_tank_glass_*.json" "%DEST_NEOFORGE%\"
if errorlevel 1 (
    echo ERROR: Failed to copy glass models to NeoForge resources
    pause
    exit /b 1
)
echo Done!

echo.
echo ========================================
echo SUCCESS! Models copied to NeoForge.
echo ========================================
echo.
echo Files copied to:
echo   - %DEST_NEOFORGE%
echo.

REM Optional: Show file count in destination
for /f %%A in ('dir /b "%DEST_NEOFORGE%\fish_tank_frame_*.json" 2^>nul ^| find /c /v ""') do set NEOFORGE_FRAME_COUNT=%%A
for /f %%A in ('dir /b "%DEST_NEOFORGE%\fish_tank_sand_*.json" 2^>nul ^| find /c /v ""') do set NEOFORGE_SAND_COUNT=%%A
for /f %%A in ('dir /b "%DEST_NEOFORGE%\fish_tank_glass_*.json" 2^>nul ^| find /c /v ""') do set NEOFORGE_GLASS_COUNT=%%A

echo NeoForge: %NEOFORGE_FRAME_COUNT% frame models, %NEOFORGE_SAND_COUNT% sand models, %NEOFORGE_GLASS_COUNT% glass models
echo.

pause
