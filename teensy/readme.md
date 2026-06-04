# LED2 Teensy Design Summary

This document captures the current design direction for the round glass-pebble interactive table. It is intended as the reference for future firmware and hardware work on the Teensy-based implementation of tap sensing, rim touch sensing, sound generation, LED control, and optional home automation integration.

## Project goals

The table is a round panel made from glass pebbles epoxied to a plexiglass sheet, with a loop of aluminum stock around the rim and a 900-pixel side-emitting RGB LED installation underneath. The system should support multiple interactive modes including a handpan-like musical instrument, a Simon-style memory game, and controller-style game interactions such as Pong, all with tightly coordinated sensing, sound, and light behavior.

Key requirements:

- Detect taps on the round glass-pebble surface.
- Potentially detect touch or touch-position on the aluminum rim.
- Generate responsive synthesized sound.
- Drive the existing 900 LEDs with low enough latency for game-like interactions.
- Optionally integrate with Wi-Fi and home automation.
- Keep hardware cost reasonable.

## Core controller decision

The Teensy 4.0 is the preferred central controller.

Reasons:

- 600 MHz Cortex-M7 gives ample DSP headroom for acoustic TDOA calculations, animation logic, and audio synthesis.
- It is a lower-cost controller than a Pixelblaze while being more suitable for tightly synchronized sensing, sound, and game logic.
- It supports high-speed ADC work suitable for piezo-based sensing.
- It has a mature audio ecosystem via the Teensy Audio Library and Teensy Audio Shield.
- It can also drive the LED strips directly, removing the need for split responsibility across multiple real-time controllers.

The earlier idea of retaining the Pixelblaze was reconsidered. For ambient LED work a Pixelblaze is excellent, but for tightly timed interactions where taps, notes, game state, and LED effects must all line up precisely, a single-controller architecture centered on the Teensy is the better fit.

## Surface tap sensing on the round pebble panel

The primary sensing method for the main circular surface is piezo-based acoustic time-difference-of-arrival (TDOA) triangulation.

### Sensing concept

When the user taps the panel, vibration propagates through the plexiglass substrate. Multiple piezo contact sensors mounted on the underside or edges of the panel detect the arrival of that vibration at slightly different times. The Teensy measures those timing differences and estimates tap position.

### Expected performance

This approach is expected to be practical and accurate enough for musical and game interaction. The exact accuracy depends on mechanical construction, mounting, signal conditioning, sampling method, and calibration, but the design target is roughly:

- Easy implementation target: around 1–2 cm.
- Better tuned implementation target: around 5–10 mm.
- Research-grade results can be tighter, but the glass-pebble geometry will likely limit precision compared with flat optimized surfaces.

### Sensor count and placement

At least 3 sensors are required for 2D localization, but more than 3 improves robustness and practical accuracy.

Recommended configuration:

- Preferred starting point: 4 piezo sensors.
- Optional expansion: 5 sensors if center-area ambiguity or edge behavior becomes problematic.

For a circular panel, the recommended 4-sensor placement is around the rim at approximately:

- 45°
- 135°
- 225°
- 315°

This is preferred over simply placing them at cardinal points. A possible 5th sensor can be mounted near the center underside if needed later.

### Recommended piezo sensor parts

The original Murata suggestion is no longer practical due to availability. The currently preferred DigiKey-available part is:

- Same Sky (formerly CUI Devices) **CPT-2746-L100**

Notes:

- 27 mm piezo element with leads.
- Suitable as a bare piezo contact sensor.
- Inexpensive and available.
- Functionally suitable for TDOA sensing.

Suggested quantity:

- Buy 8 total.
- Initial use: 4 for panel sensing.
- Optional later use: 2 for rim acoustic sensing.
- Keep 2 as spares.

Important:

- Use bare piezo elements, not piezo buzzer modules with resonant housings.
- Mount mechanically for repeatability on the underside or edge of the plexiglass, not on top of the pebble surface.

## Piezo front-end electronics

The piezo elements should not be wired directly to the Teensy ADC pins. They are high-impedance sources and benefit from buffering.

### Buffering choice

Use one JFET buffer per analog sensor channel.

Preferred part:

- **2N5484** N-channel JFET, TO-92 package

Why 2N5484:

- Good fit for 3.3 V operation.
- High input impedance.
- Better/more predictable for this build than the 2N5457 in this application.
- A 10-piece pack is an appropriate purchase.

### Quantity of JFETs

Planned usage:

- 4 for panel piezo sensors.
- 1 for rim resistive-position sensing.
- 5 total used in the baseline build.
- 10 purchased total is appropriate, leaving spares.

### JFET role

The JFET is used as a high-impedance buffer so the sensor voltage is not loaded down by the ADC input. This is especially important for both piezo sensors and any high-impedance resistive/capacitive touch arrangement on the rim.

### Design notes for analog front end

- One front-end channel per piezo.
- Keep sensor leads short where possible.
- Use shielding and careful grounding.
- Add input protection and filtering as needed.
- Software calibration of baseline offsets is expected.

## Rim interaction sensing

The table rim is a loop of aluminum stock. Several approaches were considered, but the preferred one for continuous touch-position sensing is a resistive position divider concept.

### Preferred rim approach: resistive voltage divider

The idea is to make the rim behave like a 1D position-sensitive resistive element by adding a thin resistive film or coating, or an equivalent distributed resistive layer, along the loop. A touch at a point on the rim then corresponds to a measurable position-dependent voltage. That signal is buffered by a JFET and read by the Teensy ADC.

This is attractive because:

- It supports continuous position sensing on the rim.
- It is visually unobtrusive.
- It can potentially support slide gestures around the circular perimeter.
- It fits the round-table form factor naturally.

### Other rim options considered

- Capacitive zone sensing using multiple electrodes and an MPR121-like controller.
- Acoustic rim tap localization using additional piezos on the aluminum.

These remain fallback or future options, but the current preferred direction is the resistive rim solution.

## LED control

The underside LED installation currently consists of **900 total pixels arranged as 3 strips of 300 each**, already wired and functioning with 5 V injection/boosting as needed.

### Current design decision

Do **not** split the installation into 8 segments unless later testing proves it necessary. The existing 3-strip arrangement should be preserved.

The Teensy should take over driving the strips directly in the same practical style as the Pixelblaze, using one output pin per strip.

### Recommended LED approach on Teensy

Use Teensy-compatible FastLED support, ideally with the modern DMA-oriented approach discussed during planning.

Target architecture:

- Strip 1: 300 LEDs on one Teensy output pin.
- Strip 2: 300 LEDs on a second Teensy output pin.
- Strip 3: 300 LEDs on a third Teensy output pin.

Important design goals:

- Preserve existing physical strip layout and power injection.
- Reuse the existing 3-strip electrical topology.
- Centralize all interaction logic, light response logic, and mode logic in the Teensy.

### 3.3V logic vs. 5V LED data signal

The Teensy 4.0 outputs **3.3V logic** on all GPIO pins and is **not 5V tolerant** — do not apply 5V to any Teensy pin. WS2812B strips powered at 5V technically require a data HIGH of ≥ 3.5V, which puts the Teensy's 3.3V output just below the guaranteed threshold.

**Initial approach (try first):** Wire the data lines directly from Teensy to strip DIN with a **330Ω series resistor** on each line. In practice this often works reliably, especially with short wire runs (< 30 cm) from Teensy to the first pixel, because the first LED re-drives the signal at full 5V for the rest of the chain.

**If flickering or data errors occur, add a level shifter:**

- **74AHCT125** quad buffer (DIP-14, ~$0.50 at DigiKey) — one chip handles all 3 data lines with one spare channel. Wiring: 3.3V data in on A pins, 5V-referenced output on Y pins, VCC to 5V, GND to GND.
- Alternatively, the **SN74HCT245** octal buffer handles all 3 strips on one chip with more headroom.
- A 330Ω resistor between the level shifter output and each strip DIN is still recommended regardless.

### Why move LED control to the Teensy

The table will support interaction-heavy modes where tap detection, sound generation, and LED response must be tightly synchronized. A single controller owning:

- sensing,
- game logic,
- sound synthesis,
- and LED rendering

will simplify implementation and minimize timing mismatch.

### Pixelblaze status

The Pixelblaze should not be the primary runtime controller for the main interactive system design. It may still be useful for experiments, fallback LED-only work, or prototyping visual ideas, but the current project architecture should assume the Teensy owns the LEDs during interactive operation.

## Sound generation

The Teensy 4.0 should also generate sound effects and synthesizer-like audio in response to touches and taps.

### Audio architecture

Use the **Teensy Audio Library** together with a Teensy-compatible audio output board.

Preferred audio hardware:

- **Teensy Audio Shield Rev D2** for Teensy 4.x

Codec used:

- **SGTL5000**

This provides:

- Stereo audio output.
- Tight integration with the Teensy Audio Library.
- Support for synth-style voices, oscillators, filters, envelopes, effects, and general event-driven sound design.

### Why Teensy is suitable for audio

The Teensy 4.0 has enough compute budget to:

- acquire and process piezo events,
- render LED effects,
- and generate synthesized audio

within the same controller. This is a major reason it is preferred over distributing responsibilities among separate creative controllers.

### Intended sound/interaction use cases

Planned modes include:

- **Digital handpan**: tap position maps to note, timbre, and visual response.
- **Simon-like game**: tones and lighting cues are tightly paired with input validation.
- **Pong-like mode**: rim position may control a paddle while sound effects respond to collisions and scoring.

## Optional Wi-Fi and home automation

The Teensy 4.0 does not have built-in Wi-Fi, but external Wi-Fi is still possible.

### Preferred Wi-Fi strategy

Use a separate ESP32 connected to the Teensy over UART.

Why:

- Keeps the Teensy focused on real-time sensing, DSP, audio, and LED work.
- Allows Wi-Fi, Home Assistant, MQTT, OTA, and remote control features to evolve independently.
- Simple hardware interface.
- Lower risk to real-time timing than making the main controller handle networking directly.

Likely connection:

- Teensy Serial1 (or another hardware UART) to ESP32 serial pins.

Potential software responsibilities:

- Teensy: local sensing, sound, LEDs, mode logic.
- ESP32: remote commands, telemetry, Home Assistant integration, MQTT, simple web UI, OTA updates.

## System architecture summary

Current preferred architecture:

- **Teensy 4.0** as main controller
- **4 × CPT-2746-L100 piezo sensors** for panel TDOA sensing
- **2N5484 JFET buffers** on sensor channels
- **Resistive-position aluminum rim** with JFET-buffered ADC readout
- **Teensy Audio Shield Rev D2** for audio output
- **Existing 3 × 300 LED strips** driven directly by the Teensy
- **Optional ESP32** for Wi-Fi / automation

## Baseline bill of materials direction

Main planned parts:

- Teensy 4.0
- Teensy Audio Shield Rev D2
- 8 × Same Sky CPT-2746-L100 piezo elements
- 10 × 2N5484 N-channel JFETs
- Passive components for analog front ends, protection, biasing, and filtering
- Optional ESP32 development board/module for Wi-Fi and home automation

## Firmware planning notes for future sessions

Future Teensy firmware work should be organized around the following subsystems.

### 1. Sensor acquisition

- High-speed ADC capture for piezo channels
- Event detection / thresholding
- Rim position ADC input
- Calibration routines

### 2. Tap localization

- Multi-channel arrival detection
- TDOA estimation
- Circular table coordinate mapping
- Mechanical calibration and compensation

### 3. Interaction engine

- Central event router for taps, touches, rim gestures, and game actions
- Multiple runtime modes
- Shared timing base for light and sound

### 4. Audio engine

- Note mapping from tap position
- Polyphonic or layered synth voices as needed
- Mode-specific sound sets
- Event-driven sound effects for games

### 5. LED engine

- Frame rendering for 900 LEDs across 3 strips
- Coordinate mapping from physical table geometry to LED layout
- Shared response timing with sound engine
- Mode-specific animation logic

### 6. Communications / remote control

- Optional UART protocol to ESP32
- Mode switching
- Telemetry / diagnostics
- Home automation hooks

## Open design questions

These items remain to be finalized during implementation:

- Exact mechanical mounting method for piezo sensors.
- Final analog front-end schematic for piezos and rim buffer.
- Exact ADC acquisition strategy on Teensy for multi-channel TDOA.
- Final LED driver/library choice and pin assignment for the 3-strip arrangement.
- Coordinate transform between physical tap location and spiral LED layout.
- Whether rim acoustic sensing will be added in addition to resistive sensing.
- Final remote-control protocol between Teensy and optional ESP32.

## Practical next steps

Recommended next implementation sequence:

1. Build one buffered piezo channel on breadboard and validate clean signal capture on Teensy.
2. Build all 4 piezo channels and test tap timing repeatability on a mock panel.
3. Prototype the resistive rim sensor and verify stable position reading.
4. Bring up Teensy Audio Shield and generate basic synth/test tones.
5. Move one existing 300-pixel strip from Pixelblaze to Teensy and verify stable control.
6. Expand to all 3 strips and establish a physical-to-LED coordinate map.
7. Implement a minimal event pipeline: tap -> localized position -> note -> LED flash.
8. Add mode framework for handpan / Simon / Pong.
9. Add optional ESP32 integration later, once core interaction is stable.
