# Bike Repair Station Status

A small Java Swing app that displays the motion-sensor station's live status from
the Arduino LED over USB serial. The other station entries are static examples
and currently display “Not in use”.

## Run locally

1. Upload `motionSensorLED.ino` to the Arduino.
2. Connect the Arduino to the computer over USB.
3. Compile and run the Java app from the project directory:

   ```sh
   javac -d bin src/App.java
   java -cp bin App
   ```

4. In the app, select the Arduino serial port (use **Refresh** if you connected
   it after opening the app), then click **Connect**. The app scans `/dev` for
   common macOS and Linux serial device names; the port can also be entered
   manually.

The Arduino sends `LED:ON` and `LED:OFF` at 9600 baud. The app maps those
messages to “In use” and “Not in use” for Station 1. It configures the serial
device using the system `stty` command, so the current implementation supports
macOS and Linux.
