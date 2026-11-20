# Bike Repair Station Status

This Java Swing app displays whether a bike repair station is in use. The eight
dummy locations always show **Not in use**. **Goodwin Hall** displays the latest
`0` or `1` read from the Arduino as **Not in use** or **In use**, respectively.

## Run the app

Install Java 11 or later and upload
[`A6_lightSensorButton/A6_lightSensorButton.ino`](A6_lightSensorButton/A6_lightSensorButton.ino)
to the Arduino.

### Windows

Install a Java 11 or later JDK and make sure `javac` is on `PATH`. Double-click
[`run-windows.bat`](run-windows.bat). The launcher downloads jSerialComm on its
first run, compiles the app, and opens it.

### macOS or Linux

Download the
[jSerialComm 2.11.0 JAR](https://repo.maven.apache.org/maven2/com/fazecast/jSerialComm/2.11.0/jSerialComm-2.11.0.jar)
to the project root, then compile and run from the project root:

```sh
mkdir -p out
javac --release 11 -cp jSerialComm-2.11.0.jar -d out src/main/java/BikeRepairStationStatus.java
java -cp "out:jSerialComm-2.11.0.jar" BikeRepairStationStatus
```

In the app, select the Arduino's serial port. The app reads it at 9600 baud and
checks for new serial data every 100 ms. Keep the Arduino Serial Monitor closed
while the app is connected, since only one application can normally use the
serial port at a time. Select Goodwin Hall to see the Arduino state. The sketch
does not send an initial value at startup; it sends the toggle value when the
button is pressed. After connecting, the app displays "Waiting for Arduino..."
until the next button press sends a value.