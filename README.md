# XMAS - LED Mapping with Pixelblaze

A Spring Boot application with OpenCV for LED detection and Pixelblaze control.

## Requirements

- Java 21+
- Gradle 8.5+
- Pixelblaze LED controller on your network

## Configuration

Configure the application in `src/main/resources/application.properties`:

```properties
# Pixelblaze Configuration
pixelblaze.host=192.168.86.65    # IP address of your Pixelblaze
pixelblaze.led-count=300         # Number of LEDs in your strip
pixelblaze.brightness=50         # Initial brightness for LED mapping (1-100)
```

All parameters can be overridden via command-line arguments where applicable.

## Getting Started

### Build the project

```bash
./gradlew build
```

### Run the LED Mapper

Maps physical LED positions using a webcam:

```bash
# Uses defaults from application.properties
./gradlew bootRun --args="--map-leds"

# Override LED count
./gradlew bootRun --args="--map-leds 300"

# Override LED count and initial brightness
./gradlew bootRun --args="--map-leds 300 100"
```

**Controls:**
- `SPACE` - Start/Stop mapping
- `S` - Save positions to file
- `R` - Reset all detected positions
- `D` - Save debug frame
- `Q` - Quit

The mapper outputs a JavaScript file compatible with Pixelblaze's Pixel Mapper.

### Pixelblaze Test Commands

```bash
# Test connection with color sequence
./gradlew bootRun --args="--pixelblaze-test"

# Light a single pixel
./gradlew bootRun --args="--pixelblaze-pixel <index> <color>"
# Example: ./gradlew bootRun --args="--pixelblaze-pixel 0 blue"

# Blink a pixel
./gradlew bootRun --args="--pixelblaze-blink <index> <color>"

# Light all pixels white
./gradlew bootRun --args="--pixelblaze-all-white"

# Turn off all pixels
./gradlew bootRun --args="--pixelblaze-off"

# Run detection sequence
./gradlew bootRun --args="--pixelblaze-sequence"
```

Colors: `blue`, `red`, `green`, `white`

### Run tests

```bash
./gradlew test
```

## Pixelblaze Pattern

The controller uses JSON `setVars` commands. Your Pixelblaze pattern should handle:

```javascript
export var mode = 0     // 0 = clear all, 1 = light single LED, 2 = light all
export var ledIndex = 0 // LED index to light (when mode = 1)
export var r = 0, g = 0, b = 0  // RGB values (0-1 range)
```

## OpenCV Integration

This project uses the [OpenPnP OpenCV package](https://github.com/openpnp/opencv) which bundles OpenCV native libraries for multiple platforms (Windows, macOS, Linux). The native library is automatically loaded at application startup.

### Supported Platforms

- Windows (x86_64)
- macOS (x86_64, arm64)
- Linux (x86_64, arm64)

## Project Structure

```
src/
├── main/
│   ├── java/com/xmas/
│   │   ├── XmasApplication.java          # Main application entry point
│   │   ├── LedMappingSession.java        # LED mapping with webcam
│   │   ├── config/
│   │   │   └── OpenCVConfig.java         # OpenCV configuration
│   │   └── pixelblaze/
│   │       ├── PixelblazeController.java # WebSocket controller
│   │       └── PixelblazeTestRunner.java # Test commands
│   └── resources/
│       └── application.properties        # Application configuration
└── test/
    └── java/com/xmas/
        └── XmasApplicationTests.java     # Tests including OpenCV verification
```

## OpenCV Version

This project uses OpenCV 4.9.0 via the OpenPnP packaging.
