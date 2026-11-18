const int PIR_PIN = 3;                    // motion sesnor connection
const int LED_PIN = 13;                   // led connection
const unsigned long LED_ON_MS = 20;        // how long the LED stays on after the last motion, in milliseconds
const unsigned long WARMUP_MS = 30000;    // how long to wait at startup for the PIR to settle (30 s)

unsigned long lastMotion = 0;             // stores the time (from millis()) when motion was last detected
bool ledOn = false;                       // tracks whether the LED is currently on (true) or off (false)

void setup() {                            // runs once when the Arduino powers up or resets
  Serial.begin(9600);                     // send LED state to the local status app
  pinMode(PIR_PIN, INPUT);                // set the motion sensor pin as an input so we can read the sensor
  pinMode(LED_PIN, OUTPUT);               // set the LED pin as an output
  digitalWrite(LED_PIN, LOW);             // make sure the LED starts off
  Serial.println("LED:OFF");
  delay(WARMUP_MS);                       // pause for the warm-up time to ignore false triggers while the sensor settles :)
}

void loop() {                             // runs over and over forever after setup() finishes
  static unsigned long lastStatus = 0;

  if (digitalRead(PIR_PIN) == HIGH) {     // if the sensor output is HIGH, motion is being detected
    lastMotion = millis();                // record the current time, restarting the countdown on every detection
    if (!ledOn) {                         // only do this if the LED is not already on
      digitalWrite(LED_PIN, HIGH);        // turn the LED on
      ledOn = true;                       // remember that the LED is now on
      Serial.println("LED:ON");
    }
  }

  if (ledOn && (millis() - lastMotion >= LED_ON_MS)) {  // if the LED is on AND enough time has passed since the last motion
    digitalWrite(LED_PIN, LOW); // turn the LED off
    ledOn = false; // remember that the LED is now off
    Serial.println("LED:OFF");
  }

  if (millis() - lastStatus >= 1000) {    // periodically report state for apps that connect after startup
    Serial.println(ledOn ? "LED:ON" : "LED:OFF");
    lastStatus = millis();
  }
}