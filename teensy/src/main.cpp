// Hello World Blink for Teensy 4.0
// Basic LED blink program to verify hardware and PlatformIO setup

const int ledPin = 13; // Built-in LED pin on Teensy 4.0

void setup() {
  // Initialize the digital pin as an output
  pinMode(ledPin, OUTPUT);
  
  Serial.begin(9600);
  Serial.println("Teensy 4.0 Hello World!");
}

void loop() {
  digitalWrite(ledPin, HIGH);   // Turn the LED on
  Serial.println("LED ON");
  delay(1000);                  // Wait for 1 second
  
  digitalWrite(ledPin, LOW);    // Turn the LED off
  Serial.println("LED OFF");
  delay(1000);                  // Wait for 1 second
}