@echo off
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo Java 8 or later JDK is required. Install a JDK and add it to PATH.
    goto failed
)

where javaw >nul 2>nul
if errorlevel 1 (
    echo Java 8 or later JDK is required. Install a JDK and add it to PATH.
    goto failed
)

if not exist "jSerialComm-2.11.0.jar" (
    echo Downloading jSerialComm...
    powershell -NoProfile -Command "try { Invoke-WebRequest -ErrorAction Stop -Uri 'https://repo.maven.apache.org/maven2/com/fazecast/jSerialComm/2.11.0/jSerialComm-2.11.0.jar' -OutFile 'jSerialComm-2.11.0.jar' } catch { exit 1 }"
    if errorlevel 1 (
        echo Could not download jSerialComm. Check your internet connection.
        goto failed
    )
)

if not exist "out" mkdir "out"
javac -source 8 -target 8 -cp "jSerialComm-2.11.0.jar" -d "out" "src\main\java\BikeRepairStationStatus.java"
if errorlevel 1 goto failed

start "" javaw -cp "out;jSerialComm-2.11.0.jar" BikeRepairStationStatus
exit /b 0

:failed
echo.
pause
exit /b 1
