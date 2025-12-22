# XMAS - Spring Boot OpenCV Application

A Spring Boot application with OpenCV Java bindings for image processing.

## Requirements

- Java 21+
- Gradle 8.5+

## Getting Started

### Build the project

```bash
./gradlew build
```

### Run the application

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`.

### Run tests

```bash
./gradlew test
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
│   │   ├── XmasApplication.java      # Main application entry point
│   │   └── config/
│   │       └── OpenCVConfig.java     # OpenCV configuration
│   └── resources/
│       └── application.properties    # Application configuration
└── test/
    └── java/com/xmas/
        └── XmasApplicationTests.java # Tests including OpenCV verification
```

## OpenCV Version

This project uses OpenCV 4.9.0 via the OpenPnP packaging.

