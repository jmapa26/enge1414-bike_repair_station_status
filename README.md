# Bike Repair Station Status

A small Java Swing app that displays the motion-sensor station's live status from
the Arduino LED over USB serial. The other station entries are static examples
and currently display “Not in use”.

## Run locally

1. Connect the PIR motion sensor output to Arduino digital pin 3 and the LED
   indicator to digital pin 13. Use the Arduino board's ground and appropriate
   power pins for the sensor and LED.
2. Upload `motionSensorLED.ino` to the Arduino.
3. Connect the Arduino to the computer over USB.
4. Compile and run the Java app from the project directory:

   ```sh
   javac -d bin src/App.java
   java -cp bin App
   ```

5. In the app, select the Arduino serial port (use **Refresh** if you connected
   it after opening the app), then click **Connect**. On macOS and Linux the
   app scans `/dev`; on Windows it discovers COM ports from the Windows device
   registry. A port can also be entered manually.

The Arduino waits 30 seconds after startup to let the PIR sensor warm up. When
motion is detected, it turns the LED on and sends `LED:ON` at 9600 baud. The
LED remains on for 20 milliseconds after the latest motion, then the Arduino
turns it off and sends `LED:OFF`. It repeats the current state once per second
for apps that connect after startup.

The Java app maps `LED:ON` and `LED:OFF` to “In use” and “Not in use” for
**Station 1 (motion sensor)**. The other entries are static placeholders. Serial
configuration uses `stty` on macOS/Linux and `mode` on Windows.

## Windows executable

Use a 64-bit Windows JDK that includes `jpackage` (JDK 14 or newer) and install
WiX Toolset 3 (required by `jpackage` for `.exe` installers). Open PowerShell
in the project directory and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\build_windows.ps1
```

This creates a native installer in `dist\` named
`Bike Repair Station Status-1.0.0.exe`. Run the installer, then launch
**Bike Repair Station Status** from the Start menu or desktop shortcut.
The executable should be built on Windows; `jpackage` cannot create Windows
packages from macOS or Linux.
