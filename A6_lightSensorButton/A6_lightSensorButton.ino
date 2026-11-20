// this code turns on an array of leds only when the light level of the environment falls below a certain threshold, 
// turning the leds off when the light level is above the threshold
// it also toggles a value between 0 and 1 each time the button is pressed and prints it to the serial window

const int photoPin = A0; // photocell connection (analog input)
const int ledPin = 13; // led connection
const int buttonPin = 2; // button connection
const int lightThreshold = 400; // led turns on when the light level is below this value 
                                // (may have to adjust based on surroundings, this value was found for use in Frith)

bool lastButtonState = false; // remembers whether the button was pressed on the previous loop
int toggleValue = 0; // the on/off value, flips between 0 and 1 with each press
 
void setup() { 
  Serial.begin(9600); // set up serial window
  pinMode(ledPin, OUTPUT); // set the LED pin as an output
  pinMode(buttonPin, INPUT_PULLUP); // button uses the internal pull-up resistor (wire the button between pin 2 and GND)
  digitalWrite(ledPin, LOW); // make sure the LED starts off 
}
 
void loop() {
  int lightLevel = analogRead(photoPin); // read the light level (0-1023)
  bool buttonPressed = (digitalRead(buttonPin) == LOW); // true when pressed (pull-up makes pressed = LOW)

  // for debugging
  // Serial.print("Light level: "); // label for the reading
  // Serial.println(lightLevel); // print reading and move to a new line
  // delay(200); // pause between readings so the output is readable

  // flip the value only at the moment the button goes from not pressed to pressed
  if (buttonPressed && !lastButtonState) {
    toggleValue = 1 - toggleValue; // 0 becomes 1, 1 becomes 0
    Serial.print("Toggle value: ");
    Serial.println(toggleValue);
  }
  lastButtonState = buttonPressed; // remember the state for the next loop
 
  if (lightLevel < lightThreshold) { // if it's darker than the threshold,
    digitalWrite(ledPin, HIGH); // turn the LED on
  }
  else {
    digitalWrite(ledPin, LOW);// otherwise turn the LED off
  }
 
  
}