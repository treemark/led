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

Audio synthesis runs on the Raspberry Pi rather than the Teensy. This keeps all game-state-dependent logic — note selection, patch choice, timing — on the same device that owns the game loop and SDF renderer, eliminating the need to pass audio intent across a device boundary. The Pi has significantly more synthesis capability than the Teensy Audio Library, and the ~5–15 ms latency of Pi audio is imperceptible for this application.

### Audio hardware on Pi

Three options in order of preference:

- **HiFiBerry DAC2 HD** (~$45–55) — Pi HAT connecting over I2S directly to the GPIO header. High-quality stereo output, excellent driver support under Pi OS, very low latency. Recommended for the full-featured build.
- **PCM5102A / PCM5122 I2S DAC breakout** (~$5–15) — bare I2S DAC board, same signal path as HiFiBerry at lower cost and smaller footprint.
- **USB audio DAC** (~$10–30) — any USB audio interface (e.g. Behringer UMC22) recognized instantly as a standard ALSA device. Highest compatibility, slightly higher latency than I2S.

The **Teensy Audio Shield Rev D2 and SGTL5000 codec are removed from the design**. The Teensy no longer handles audio.

### Audio latency

Linux audio latency with JACK on a Pi 5 with a realtime kernel achieves ~5–10 ms round-trip. Pipewire with low-latency configuration achieves ~10–20 ms. Both are well within the ~20–40 ms human audio-visual sync tolerance and imperceptible for musical interaction at this scale.

A/V sync between audio and LEDs: the Pi schedules audio immediately after queueing an LED frame packet for USB transmission. USB serial latency is sub-millisecond in practice for these packet sizes, so audio and LEDs remain perceptually locked. If needed, a fixed software offset can be dialed in after measurement on the final hardware.

### Audio synthesis options on Pi

**SuperCollider (recommended)**

[SuperCollider](https://supercollider.github.io) is a dedicated real-time audio synthesis environment with its own server (`scsynth`) that accepts OSC messages. The Java game loop sends OSC trigger messages (`{ note: C4, synthDef: handpan_mallet, velocity: 0.8, features: [...] }`) and SuperCollider handles all synthesis. SynthDefs (synthesis patch definitions) can be as simple or complex as needed — physical modeling, granular synthesis, FM, additive. SuperCollider runs well on Pi 5 and has a large library of existing handpan and percussion patches.

**C++ audio synthesis libraries via JNI / FFM**

Several high-quality C++ audio synthesis libraries can be called from Java via JNI (Java Native Interface) or the newer Foreign Function & Memory API (FFM, available in Java 22+):

- **Synthesis ToolKit (STK)** — Perry Cook / Gary Scavone's synthesis library. Includes physical models for struck membranes, bells, and bar percussion — directly applicable to handpan-like timbres. MIT licensed, compiles cleanly on ARM64.
- **JUCE** — comprehensive C++ audio framework with synthesis, effects, and plugin support. Can be compiled as a shared library and called via FFM. More complex to integrate but extremely capable.
- **miniaudio** — single-header C audio library, easy to wrap with JNI for low-level PCM output if a lightweight path is preferred.
- **Maximilian** — lightweight C++ audio synthesis library, simple API, easy JNI wrapping, good for oscillators, envelopes, and filters.

The FFM approach (Java 22+) is preferred over JNI for new code — it allows calling native C functions directly from Java without writing C wrapper code, using `MemorySegment` and `Linker` APIs.

**Beads (pure Java)**

[Beads](http://www.beadsproject.net) is a Java audio library designed for real-time synthesis with low latency. No native dependencies. Suitable for simpler synthesis needs and faster prototyping. Less capable than SuperCollider or STK for complex physical modeling but zero integration friction.

### Recommended synthesis path

Start with **SuperCollider + OSC** from the Java game loop. It provides the richest synthesis capability with the least integration complexity — no JNI, no native builds, just OSC messages over localhost. If SuperCollider proves too heavy or the synthesis needs are modest, fall back to Beads for pure Java simplicity or STK via FFM for physical modeling quality without SuperCollider's overhead.

### Intended sound/interaction use cases

Planned modes include:

- **Digital handpan**: tap position maps to note; acoustic feature vector (rise time, spectral centroid, etc.) selects timbre and envelope. Mallet strike produces a pure sine-like tone; palm slap produces a deep resonant bass.
- **Simon-like game**: tones and lighting cues are tightly paired with input validation.
- **Pong-like mode**: rim position controls a paddle; sound effects respond to collisions and scoring.

## Coprocessor: Raspberry Pi 5

The Teensy 4.0 does not have built-in Wi-Fi or the compute resources needed for the higher-level parts of this project. A **Raspberry Pi 5** is the single coprocessor in the current architecture and handles networking, AI, voice, computer vision, audio synthesis, and game-loop execution, connected to the Teensy over USB serial.

### Raspberry Pi 5 responsibilities

The Raspberry Pi 5 is the system host for all non-hard-real-time tasks:

- Computer vision: MediaPipe hand tracking, fisheye coordinate transform, gesture and object recognition
- Voice input: Porcupine wake word, Rhino intent recognition, Whisper STT (can run `whisper.cpp` locally on Pi 5 with no cloud dependency)
- LLM API calls for AI-assisted SDF animation generation, or local inference via `llama.cpp` for fully offline operation
- Wi-Fi, Home Assistant integration, MQTT
- Web UI, OTA updates, SSH access
- Audio synthesis and patch selection
- Game loop, scene state, physics, and impact interpretation
- Structured event routing to Teensy over USB serial

Connection: Teensy USB port to Pi USB host port, appearing as `/dev/ttyACM0`. The Teensy's built-in USB controller handles the CDC ACM serial device — no additional hardware required. The Pi can also power the Teensy over this same cable.

Software responsibilities by device:

- **Teensy**: real-time sensing, TDOA localization, acoustic feature extraction, raw RGB frame output to LEDs
- **Raspberry Pi**: impact interpretation, audio synthesis, SDF rendering, game loop, physics, AI, CV, voice, networking, remote control

Current preferred architecture:

- **Teensy 4.0** as real-time I/O controller (sensing, feature extraction, LED frame output)
- **4 × CPT-2746-L100 piezo sensors** for panel TDOA sensing and acoustic feature extraction
- **2N5484 JFET buffers** on sensor channels
- **Resistive-position aluminum rim** with JFET-buffered ADC readout
- **Existing 3 × 300 LED strips** driven directly by the Teensy (raw RGB frames received from Pi over USB serial)
- **Raspberry Pi 5** as game loop host, SDF renderer, audio synthesizer, CV processor, voice, AI, and networking
- **HiFiBerry DAC2 HD** (or equivalent I2S/USB DAC) on Pi for audio output
- **M12 fisheye camera** mounted center-up through a hole in the plexiglass, connected to Pi
- **INMP441 MEMS microphone** connected to Pi over I2S for voice input
- **Raspberry Pi 5** as alternative coprocessor if CV, audio, and game loop hosting are not needed (lower cost)

## Baseline bill of materials direction

Main planned parts:

- Teensy 4.0
- 8 × Same Sky CPT-2746-L100 piezo elements
- 10 × 2N5484 N-channel JFETs
- Passive components for analog front ends, protection, biasing, and filtering
- Raspberry Pi 5 (preferred coprocessor) or Raspberry Pi 5 (lower-cost alternative)
- HiFiBerry DAC2 HD Pi HAT (preferred audio output) or PCM5102A I2S DAC breakout or USB audio DAC
- M12 fisheye board camera with 180° lens (full-featured build)
- INMP441 I2S MEMS microphone
- IR LED ring for CV illumination (optional but recommended)
- Powered stereo speakers or amplifier for audio output

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

### 3. Acoustic feature extraction

- 512–1024 sample capture window at ~96 kHz via ADC DMA after tap threshold crossing (~5–10 ms window)
- Extract five float features per tap event:
  - **Rise time**: time from threshold crossing to peak amplitude (ms)
  - **Peak amplitude**: normalized peak signal level (0.0–1.0)
  - **Spectral centroid**: frequency center of mass estimated via Goertzel algorithm on 4–6 bands (Hz)
  - **Decay time constant**: envelope fall-off rate after peak (ms)
  - **HF energy ratio**: energy above ~5 kHz as fraction of total energy (0.0–1.0)
- No classification on Teensy — raw floats transmitted upstream; Pi interprets them in full game/mode context
- Total feature extraction time well under 1 ms after capture window

### 4. LED frame output

- Receive raw RGB frame (900 × 3 bytes) from Pi over USB serial
- Push frame to 3 × 300 LED strips via FastLED DMA
- No SDF evaluation, audio, or game logic on Teensy

### 5. Communications

- USB CDC ACM serial transport over the Teensy's built-in USB interface
- Existing framed binary protocol can be reused: `[ HEADER (2 bytes) | MSG_TYPE (1 byte) | LENGTH (2 bytes) | PAYLOAD | CRC-16 (2 bytes) ]`
- **Teensy → Pi**: tap event packet `{ x, y, rise_time, peak_amp, spectral_centroid, decay_tc, hf_ratio, rim_angle }` as compact binary floats
- **Pi → Teensy**: `RGB_FRAME` message containing 900 × RGB bytes, plus optional configuration packets (`CONFIG`, `PING`, `BRIGHTNESS`, `CALIBRATION`)
- Java side uses **jSerialComm** on `/dev/ttyACM0`, reusing the proven binary framing, message typing, length field, and CRC check from previous projects
- USB serial bandwidth is ample for both upstream feature events and downstream raw frame streaming; 2,700-byte RGB frames transmit in well under 1 ms in practice
- Telemetry and diagnostics can use the same message framing with additional `MSG_TYPE` values

## Open design questions

These items remain to be finalized during implementation:

- Exact mechanical mounting method for piezo sensors.
- Final analog front-end schematic for piezos and rim buffer.
- Exact ADC acquisition strategy on Teensy for multi-channel TDOA.
- Final LED driver/library choice and pin assignment for the 3-strip arrangement.
- Coordinate transform between physical tap location and spiral LED layout.
- Whether rim acoustic sensing will be added in addition to resistive sensing.
- Final USB message schema and CRC polynomial for the Pi↔Teensy protocol.

## Practical next steps

Recommended next implementation sequence:

1. Build one buffered piezo channel on breadboard and validate clean signal capture on Teensy.
2. Build all 4 piezo channels and test tap timing repeatability on a mock panel.
3. Prototype the resistive rim sensor and verify stable position reading.
4. Bring up USB serial framing between Teensy and Pi using the existing binary protocol and jSerialComm on the Pi.
5. Move one existing 300-pixel strip from Pixelblaze to Teensy and verify stable control.
6. Expand to all 3 strips and establish a physical-to-LED coordinate map.
7. Implement a minimal event pipeline: tap -> localized position + acoustic feature vector -> note selection on Pi -> LED flash + audio trigger.
8. Add mode framework for handpan / Simon / Pong.
9. Add optional Raspberry Pi integration later, once core interaction is stable.

## Graphics rendering pipeline

The 900 LEDs are not a conventional pixel buffer — they are sparse, 1D-indexed points with known 2D physical positions. The rendering model follows the Pixelblaze `render2D(index, x, y)` pattern: instead of writing to a framebuffer and sampling it, each LED's color is evaluated as a function of its physical coordinate. This approach is resolution-independent and scales naturally to future installations with different LED densities.

### Core rendering model

Each frame, a top-level `renderPixel` function is called once per LED, receiving the LED index and its normalized physical `(x, y)` coordinates (in the range `[-1, 1]`). It accumulates contributions from layered scene objects and returns a final color:

```cpp
Color renderPixel(int index, float x, float y) {
    Color c = backgroundColor(x, y, time);
    c = blendLayer(c, ambientAnimation(x, y, time));
    c = blendLayer(c, gameObjects(x, y, time));   // fish, paddles, Simon zones, etc.
    c = blendLayer(c, tapFlash(x, y, tapEvent));
    return c;
}
```

Each scene object implements a single `sample(float x, float y, float time)` method. The coordinate lookup table (LED index → normalized x, y) is baked once at startup from the physical spiral geometry.

### Pipeline model options

Three pipeline models were evaluated:

**Option 1: Procedural SDF-first (recommended)**

Every object is a mathematical signed distance field (SDF) function evaluated at each LED's `(x, y)` coordinate. Objects include capsule SDFs for fish bodies, circular ring SDFs for tap ripples, angular sector SDFs for handpan note zones, and capsule SDFs for Pong paddles. This approach is fully resolution-independent: the same code produces correct results at 900 LEDs or any future density. No framebuffer is required. At 600 MHz, evaluating ~54,000 ops per frame (900 LEDs × 3 fish × ~20 ops per fish) is well within budget.

**Option 2: Tiny rasterized framebuffer with bilinear sampling**

Render into a small off-chip framebuffer (e.g., 64×64 `uint8_t` RGB array), then for each LED sample it using bilinear interpolation at the LED's `(x, y)` coordinate. This makes it possible to port Canvas-style or TinySkia-style drawing calls, but wastes RAM and CPU on pixels no LED ever samples. The Teensy 4.0 has only 1 MB SRAM, which constrains framebuffer size. Latency increases compared to the SDF approach. This remains a viable fallback for effects that are difficult to express as closed-form SDFs.

**Option 3: SVG animation authoring with Raspberry Pi bridge**

Render SVG animations in a browser or JS runtime, rasterize per-frame to the LED coordinate map, and forward over serial or MQTT to the Raspberry Pi, which passes data to the Teensy. This is appealing for animation authoring and visual prototyping. However, the added latency hop makes it unsuitable for tight game interactions (Pong, Simon). Better suited as a design and prototyping tool: author fish behaviors in SVG/Canvas in a browser-based LED simulator that maps to physical coordinates, then port the underlying Bézier math to Teensy C++.

### Bézier spline objects

Complex animated shapes such as swimming fish are represented as parametric Bézier spines. A fish body uses two cubic Bézier curves (spine and belly outline). The distance from any LED to the spine is estimated using a point-to-curve SDF (approximately 4 iterations of Newton's method, ~20 arithmetic ops per LED per fish). A smooth envelope function (sin-based) defines fish width along the parameter `t`.

Scatter behavior on tap: the head control point `P3` is translated away from the tap `(x, y)` position; the tail control points follow with a spring delay. The same Bézier math that describes the fish in an SVG authoring environment maps directly to the Teensy C++ implementation.

### Planned interactive animations

The following animation types are planned, all implemented as SDF or parametric functions in the `renderPixel` pipeline:

- **Swimming fish**: Bézier spine bodies that dart away from the user's tap position, with tail-lag spring animation.
- **Tap ripples**: Expanding circular ring SDFs centered on tap `(x, y)`, fading as radius grows.
- **Ambient ocean**: Layered Perlin noise background animation as a continuous ambient layer.
- **Handpan note zones**: Angular sector SDFs that light up and sustain in response to tap-triggered notes.
- **Simon game zones**: Sector or arc SDFs with color assignments, synchronized to audio cues.
- **Pong paddle**: A capsule SDF tracking the player's angular rim position.
- **Pong ball**: A circular SDF with bounce physics in normalized coordinate space.
- **Score/collision flash**: Radial burst SDFs triggered on game events.

### Coordinate systems

Two coordinate spaces are used throughout the rendering pipeline:

- **Physical `(x, y)`**: Normalized to `[-1, 1]`. Used for all SDF and object math. Origin at table center.
- **LED index → `(x, y)` lookup table**: Baked once at startup from the physical spiral geometry of the LED installation. Each LED index maps to a fixed `(x, y)` in physical space.

The coordinate transform between tap localization output (TDOA-derived position) and normalized physical space is a required calibration step. Once established, all animation modes reuse it without modification.

### Authoring and prototyping workflow

A browser-based LED simulator is recommended for animation authoring. The simulator renders the physical LED map as a scatter plot and evaluates the same `renderPixel` logic (ported to JavaScript or TypeScript) in real time. SVG and Canvas tools can be used to design and visualize fish paths, ripple timings, and zone layouts. Once a behavior is finalized in the simulator, the underlying math (Bézier evaluations, SDF functions, easing curves) is ported directly to Teensy C++ with minimal changes.

## Desktop development and emulation

To avoid repeated flash/reflash cycles during development of games, animations, and SDF graphics, the firmware should be runnable on a Mac desktop with a visual LED output. Several approaches exist across a spectrum of fidelity and setup effort.

### Option 1: Native C++ with HAL stub and SDL2 (recommended for daily development)

Write all renderer, SDF, game logic, and animation code as pure C++ with no Arduino or Teensyduino dependencies. Wrap all hardware calls (FastLED, `millis()`, ADC reads, audio) behind a thin Hardware Abstraction Layer (HAL) interface. The desktop build provides a stub HAL that renders LEDs into an SDL2 window as colored circles and injects tap events from mouse clicks. The Teensy build provides the real HAL backed by FastLED, ADC, and the audio library.

```
src/
  renderer.cpp        ← pure C++, no hardware deps
  sdf_primitives.cpp  ← pure C++
  game_fish.cpp       ← pure C++
  hal.h               ← interface only
  hal_teensy.cpp      ← real FastLED, ADC, audio
  hal_desktop.cpp     ← stub: fake tap events, SDL2 display
```

This is the most maintainable long-term approach because the same source files build and run on both targets. Full debugger support (`lldb`/`gdb`) is available on the desktop build.

### Option 2: Wokwi browser-based emulator

[Wokwi](https://wokwi.com) is a browser-based hardware emulator with support for Teensy 4.0, WS2812B LED strips, and Arduino/Teensyduino libraries. The actual sketch is uploaded and simulated visually in the browser, including animated LED strip output. No local build toolchain is required. Wokwi supports GDB-based debugging and is useful for quick smoke tests and sharing demos. Emulation fidelity is not perfect for tight timing, ADC noise, or audio, and injecting realistic tap event streams requires custom scripting.

### Option 3: TypeScript browser simulator (pairs with AI pipeline)

Port the renderer and SDF primitives to TypeScript and run them in a browser Canvas. Each LED is rendered as a colored circle at its physical `(x, y)` coordinate. Mouse clicks inject tap events. This simulator is already referenced in the graphics pipeline section as the recommended animation authoring environment, and it is also the natural target for AI-generated SDF code (the model can generate and test TypeScript before the logic is ported to C++). The main limitation is that TypeScript and C++ are separate codebases, so subtle arithmetic differences (floating-point precision) can produce visual divergence.

### Option 4: PlatformIO native target

[PlatformIO](https://platformio.org) has a `native` build environment that compiles an Arduino/Teensyduino sketch for the host OS with most Arduino functions automatically stubbed. This keeps the project close to the actual sketch structure with good VS Code integration and minimal refactoring. FastLED's native target works; audio libraries will still need manual stubs. A simple LED visualizer can be added on top.

### Option 5: QEMU Cortex-M7 instruction-level emulation

QEMU can emulate an ARM Cortex-M7 (the Teensy 4.0 core) at the instruction level, running the actual compiled firmware binary. This gives near-perfect fidelity for logic and timing bugs and supports full GDB debugging. The significant setup cost is peripheral emulation: FastLED DMA, the audio codec, and ADC channels require custom QEMU device plugins. Best reserved for deep timing investigations rather than fast iteration.

### Comparison

| Option | Iteration speed | Fidelity | Setup effort | Best for |
|---|---|---|---|---|
| Native C++ + HAL + SDL2 | Very fast | High (logic) | Medium | Daily development, debugger use |
| Wokwi | Fast | Medium | Very low | Quick tests, sharing demos |
| TypeScript simulator | Fast | Medium | Low | Animation design, AI pipeline |
| PlatformIO native | Fast | Medium-high | Low–medium | Staying close to sketch structure |
| QEMU Cortex-M7 | Slow | Very high | High | Deep timing and hardware bugs |

The recommended starting combination is the **TypeScript simulator** for animation and SDF work (feeding the AI pipeline) plus a **native C++ HAL build** once game logic complexity warrants full debugger support. These two together cover the majority of the development loop without requiring the physical Teensy.

## AI-assisted content generation

The Raspberry Pi Wi-Fi link opens the possibility of on-the-fly animation design via LLM API calls. A user could speak a request or select a prompt at the table, the Raspberry Pi forwards it to a cloud model (Claude, GPT-4o, Gemini, or similar), and the response is translated into new graphics rendered by the SDF pipeline within a few seconds. The SDF renderer is a small, well-defined interface — each object is a `sample(x, y, t)` function using a constrained vocabulary of distance functions, blend modes, and coordinate transforms — making it well-suited as a generation target for language models.

### Runtime strategies

Three approaches are available, with different trade-offs between latency, safety, and expressiveness:

**Option A: Interpreted parameter objects (recommended starting point)**

Define a fixed library of SDF primitives (capsule, circle, spiral, sine wave, noise field, etc.) with numeric parameters. The AI composes and configures them via a JSON payload rather than generating arbitrary code:

```json
{
  "type": "fish_school",
  "count": 5,
  "body_length": 0.18,
  "color": [0.2, 0.8, 1.0],
  "scatter_radius": 0.4,
  "tail_spring_k": 0.6
}
```

The Teensy deserializes this into pre-compiled `SceneObject` instances. The AI acts as a parameter designer, not a code generator. Payloads are small enough to transfer over USB serial. The primitive library grows over time as new shapes are added to firmware.

**Option B: AI-generated shader code via simulator (human-in-the-loop)**

The AI writes SDF logic in GLSL or JavaScript targeting the browser-based LED simulator. A developer reviews and approves it, then it is compiled into firmware as a new named preset. More expressive than Option A, but requires a human review step — suited for building the permanent animation library rather than real-time user requests.

**Option C: Embedded scripting runtime on Teensy (ambitious)**

Embed a tiny scripting VM on the Teensy — such as [Elk](https://github.com/cesanta/elk) (a ~4 KB JavaScript engine for embedded targets) or a custom bytecode interpreter. The AI generates scripts, the Raspberry Pi pushes them over USB serial, and the Teensy executes them inside the renderer loop. Enables true on-the-fly code generation with no human review step, but adds firmware complexity, USB serial transfer latency, and execution overhead.

### Latency and reliability summary

| Approach | Round-trip latency | Reliability | Expressiveness |
|---|---|---|---|
| JSON parameter block (Option A) | ~1–2 s (API call) | Very high | Medium |
| Simulator-approved shader (Option B) | Minutes + human review | High | Very high |
| Embedded script VM (Option C) | ~1–3 s | Medium | High |

### Teaching the model to design SDF elements

Reliable AI output depends on a well-structured prompt. Effective techniques:

- **Few-shot examples in the system prompt**: Include 5–6 existing `SceneObject` implementations (ripple, fish, handpan zone) so the model pattern-matches new requests onto the established style.
- **Constrained output format**: Request a JSON parameter block or a single `sample()` function — never an open-ended file. A smaller, well-defined target yields higher reliability.
- **Primitive classification step**: Have the model first map a user description (e.g. "jellyfish") to a named primitive from the library, then parameterize it. This keeps outputs within the known-safe set of compiled shapes.
- **Validation pass**: Ask the model to also return a one-sentence description of what the animation should look like. Use this in the simulator to sanity-check the output before deploying to the table.
- **Graceful fallback**: If the API call fails or returns an invalid payload, the Teensy continues rendering the current scene unchanged.

### Raspberry Pi responsibilities for AI integration

- Capture user request (via serial command from Teensy, or voice input — see voice command section below)
- Compose and send HTTPS request to LLM API
- Parse JSON response and validate against known primitive schema
- Forward validated parameter payload to Teensy over USB serial
- Handle errors, timeouts, and retries without blocking the Teensy render loop

## Voice command interface

The Raspberry Pi (or Raspberry Pi 5 — see hardware note below) can own voice input entirely, keeping the Teensy's real-time render loop untouched. A single INMP441 I2S MEMS microphone (~$2–5) mounted on the underside of the table rim connects to the Raspberry Pi over three wires (SCK, WS, SD) and feeds both wake word detection and command recognition.

Two broad approaches are available: on-device processing (no internet, low latency, fixed vocabulary) and cloud STT (higher accuracy, open-ended natural language, same HTTPS infrastructure as the AI generation pipeline).

### On-device options

**ESP-SR on Raspberry Pi 5**

Espressif's [ESP-SR](https://github.com/espressif/esp-sr) framework runs wake word detection and command recognition entirely on the Raspberry Pi 5, which has sufficient RAM and a vector accelerator for this workload. Supports a custom wake word (e.g. "Hey Table") and a fixed vocabulary of up to ~200 phrases defined at compile time. Latency is approximately 200–500 ms, fully offline. Requires the Raspberry Pi 5 variant rather than a plain Raspberry Pi.

**Picovoice Porcupine + Rhino (recommended on-device choice)**

[Picovoice](https://picovoice.ai) splits the problem into two focused engines:

- **Porcupine**: Wake word detection, runs on Raspberry Pi/S3 at ~1% CPU. Free tier available. Custom wake words compiled via their web console.
- **Rhino**: Speech-to-intent engine. Instead of returning a transcript, it returns structured intent and slots: `{ intent: "setMode", slots: { mode: "handpan" } }`. No transcript parsing needed — the Raspberry Pi receives clean structured data to forward directly to the Teensy over USB serial.

Vocabulary is defined in a simple grammar file and compiled to a device model. Latency is approximately 300 ms, fully offline.

### Cloud STT options

**OpenAI Whisper API**

A short PCM/WAV clip captured from the MEMS mic is sent to the Whisper API over HTTPS. Returns a full transcript in approximately 500 ms–1.5 s depending on clip length. Handles open-ended natural language, accents, and noisy environments well. Cost is approximately $0.006 per minute of audio — negligible for casual use. This is the natural choice for open-ended generative requests ("draw a galaxy", "show swimming jellyfish") since the transcript feeds directly into the existing AI content generation pipeline.

**Google Speech-to-Text / AWS Transcribe streaming**

Streaming STT APIs that return partial transcripts as the user speaks, reducing perceived latency. More complex to integrate than the Whisper REST endpoint. Better suited for longer or more conversational interactions.

### Recommended two-layer architecture

On-device and cloud approaches are complementary. The recommended design uses both:

```
MEMS microphone (INMP441, I2S)
    │
    ▼
Wake word detection — Porcupine, on-device, always listening
    │  "Hey Table" detected
    ▼
Is it a known command?
    ├─ YES → Rhino intent recognition (on-device, ~300 ms)
    │         → { intent: "setMode", mode: "pong" }
    │         → USB serial to Teensy immediately
    │
    └─ NO / open-ended → Whisper API (cloud, ~1–1.5 s)
                          → transcript
                          → LLM API for SDF/animation generation
                          → JSON parameter block to Teensy
```

Known commands (mode switching, brightness, color palette changes, game start/stop) remain fully offline and respond in ~300 ms. Creative or generative requests fall through to the cloud pipeline already designed for AI content generation.

### Hardware note: Raspberry Pi vs Raspberry Pi 5

The plain Raspberry Pi is sufficient for cloud-only STT (Whisper API) since it only needs to capture audio and make HTTPS calls. On-device wake word and intent recognition with ESP-SR or TFLite requires the **Raspberry Pi 5**, which has more RAM and a vector processing extension. Given that the Raspberry Pi 5 is pin-compatible and similarly priced, upgrading to the S3 early is recommended — it unlocks on-device speech processing without any firmware restructuring.

### Option comparison

| Approach | Latency | Offline | Vocabulary | Complexity |
|---|---|---|---|---|
| ESP-SR on Raspberry Pi 5 | ~300 ms | Yes | Fixed ~200 phrases | Low |
| Porcupine + Rhino | ~300 ms | Yes | Custom grammar | Low–medium |
| Whisper API | ~1–1.5 s | No | Open-ended | Low |
| Porcupine + Rhino + Whisper (hybrid) | 300 ms / 1.5 s | Partial | Both | Medium |
| Google / AWS streaming | ~500 ms | No | Open-ended | High |

## Computer vision (stretch goal)

A camera added to the table enables a class of interactions that go beyond what piezo TDOA sensing can provide: multi-hand tracking, hover and proximity detection, gesture recognition, user presence around the table, and physical object recognition. CV events feed into the same Teensy interaction engine as tap events, treated as an additional parallel event source.

### Preferred camera geometry: center-mounted fisheye looking up

The preferred approach is a fisheye camera mounted inside the table enclosure, looking upward through a small hole drilled in the center of the plexiglass substrate. The lens protrudes 1–2 mm above the surface, flush among the glass pebbles.

This geometry has several advantages over an overhead downward-facing mount:

- **No optical distortion from plexiglass** — the lens sees through air rather than acrylic, eliminating refractive distortion and calibration complexity.
- **No pebble scatter** — the lens has a clean unobstructed view through the hole rather than through the epoxied pebble surface.
- **Natural circle-to-circle mapping** — a 180°+ fisheye from the table center maps almost perfectly onto the round table geometry, matching the LED coordinate space with minimal warping math.
- **No occlusion** — overhead mounts can be blocked by the user's body leaning over the table; a center-up mount sees hands from below regardless of posture.
- **Hidden installation** — the camera lives entirely inside the enclosure with only the lens barrel visible.
- **IR illumination** — IR LEDs inside the enclosure pointing upward produce crisp hand silhouettes against the diffuse overhead ambient light, making CV lighting-independent.

This is the same fundamental geometry used by the original Microsoft Surface Table (2007), which used IR cameras looking up through a diffuse backlit surface.

### Physical hole and camera details

- Drill a **14–15 mm hole** in the center of the plexiglass for a standard M12 fisheye lens barrel (12 mm diameter).
- Mount the camera PCB on a small bracket below the plexiglass; thread the lens up through the hole so it protrudes 1–2 mm above the surface.
- Use a thin rubber grommet or O-ring around the lens barrel to seal against debris and dampen vibration (important for TDOA piezo sensing — the camera bracket must not conduct mechanical noise into the plexiglass).
- Mount the camera bracket on rubber standoffs / a vibration-isolated sub-frame.
- The 14 mm hole creates a minor mechanical discontinuity in the plexiglass that slightly alters vibration propagation near center. Account for this during TDOA calibration. If a 5th center piezo sensor is added later, mount it on the underside away from the camera bracket.

**Aesthetic note**: a black anodized M12 lens barrel is inconspicuous among glass pebbles, especially with pebbles arranged in a tight ring up to the hole edge. A slightly convex fisheye dome element catches light similarly to the surrounding pebbles and can read as an intentional design feature.

### Recommended camera hardware

- **ELP 1080p USB fisheye camera with 180° M12 lens** (~$25–35) — USB, works directly with Raspberry Pi and Mac/OpenCV, no IR cut filter versions available for IR use.
- **M12 mount board camera + 1.8 mm fisheye lens** (~$15–20 total) — bare board camera with swappable M12 lens, very compact, fits easily inside the table enclosure.
- **Raspberry Pi Camera Module 3 Wide** (~$35, 160° FoV) — not a true fisheye but covers most of the table; convenient if Raspberry Pi is already the CV coprocessor.

### Fisheye coordinate transform

The fisheye projection maps image pixels to physical table coordinates via a one-time calibrated transform. For an equidistant fisheye model:

```
r_pixel = sqrt((px - cx)² + (py - cy)²)   // distance from image center
θ       = r_pixel / f                        // angle from optical axis
r_table  = tan(θ) * h                        // physical radius; h = camera height below surface
```

Where `cx, cy` is the image center and `f` is the focal length in pixels. Calibrate once using a printed grid pattern placed on the table surface. The output is accurate physical `(x, y)` in the same normalized `[-1, 1]` space used by the SDF renderer.

### CV processing pipeline

A **Raspberry Pi 4 or 5** is the recommended CV coprocessor, connected to the Teensy via USB serial (same interface as the Raspberry Pi). It runs OpenCV and MediaPipe locally and emits structured events:

```
{ type: "hand_hover",   x: 0.42, y: -0.31, height: 0.08 }
{ type: "hand_tap",     x: 0.15, y:  0.22 }
{ type: "gesture",      name: "swipe_cw" }
{ type: "object_placed",x: -0.1, y:  0.3,  class: "shell" }
```

The Teensy interaction engine treats these identically to piezo tap events — the event router does not distinguish the source.

Recommended CV libraries on the Pi:

- **MediaPipe Hands**: 21 3D landmarks per hand, up to 2 hands simultaneously, ~30 fps on Pi 4/5. Covers hover, multi-touch, and gesture detection.
- **Background subtraction (MOG2/KNN)**: Fast blob tracking for basic presence detection with minimal CPU.
- **YOLOv8 nano**: Object detection at ~15–30 fps on Pi 5 for placed-object recognition.

During development, the Pi can be replaced by a Mac running the same Python OpenCV/MediaPipe pipeline, connected over USB serial, with no Teensy firmware changes required.

### Interaction possibilities enabled by CV

- **Hover anticipation**: Fish begin swimming away as a hand approaches, before any physical tap is registered.
- **Shadow casting**: The hand silhouette projected onto the LED coordinate plane is rendered as a shadow; fish and animations avoid it in real time.
- **Multi-user Pong**: Two players on opposite sides of the table each control a paddle via hand position, without needing rim touch sensing.
- **Object triggers**: A physical object placed on the surface (a shell, a stone) is recognized and triggers a corresponding animation.
- **Conductor mode**: Hand height and position modulate tempo, intensity, or color palette of a generative soundscape.

### Camera geometry comparison

| | Center fisheye (looking up, preferred) | Overhead (looking down) |
|---|---|---|
| Mounting | Hidden inside table, lens through hole | Requires arm or ceiling fixture |
| Occlusion | None — sees under hands | Body can block view |
| Table geometry match | Natural circle-to-circle | Requires perspective correction |
| Plexiglass optics | None (hole) | N/A |
| Hover height | Indirect (hand apparent size) | Direct with depth camera |
| Lighting dependence | Low with IR ring | Higher |
| Installation complexity | Low | Medium–high |
| Cost | Low | Low–medium |

## Game loop and SDF rendering on Raspberry Pi

With the Raspberry Pi 5 as coprocessor, all game logic, physics, SDF rendering, audio synthesis, and impact interpretation move to the Pi. The Teensy becomes a thin real-time I/O layer: it captures sensor events, extracts acoustic features, reports rim position, and clocks raw RGB frames out to the LED strips. No game logic, SDF evaluation, audio synthesis, or impact classification runs on the Teensy.

### Why raw RGB over USB serial

Transmitting a complete rendered RGB frame (900 LEDs × 3 bytes = 2,700 bytes) per frame is preferred over transmitting SDF parameters and rendering on the Teensy for several reasons:

- All rendering logic lives in one place: Java on the Pi. No need to keep a C++ SDF library in sync with Pi game logic.
- Resolution independence: a future installation with more LEDs requires no Teensy firmware changes, only more `renderPixel()` calls on the Pi.
- The Pi 5 ARM Cortex-A76 renders 900 LEDs in well under 1 ms even for complex scenes. Compute is not a constraint.
- The Teensy is freed from all rendering work, reducing firmware complexity significantly.
- The existing framed binary protocol over USB serial is already proven between Pi and Teensy and can be reused directly.

### USB serial bandwidth

USB CDC ACM bandwidth is ample for this workload. A single 900-LED frame is 2,700 bytes of RGB payload; even with framing, CRC, and message type overhead, this is tiny relative to practical USB serial throughput. Frame transmission time is well under 1 ms in practice, leaving plenty of headroom for 30fps or 60fps animation and for upstream sensor events at the same time.

| LED count | RGB bytes/frame | Notes |
|---|---:|---|
| 900 (current) | 2,700 | Easily fits within sub-millisecond USB serial transfers |
| 1,800 | 5,400 | Still comfortable with large headroom |
| 3,600 | 10,800 | Plausible future density without changing Teensy firmware architecture |

### Message framing

Pi↔Teensy communication reuses the existing framed binary protocol:

```
[ HEADER (2 bytes) | MSG_TYPE (1 byte) | LENGTH (2 bytes) | PAYLOAD | CRC-16 (2 bytes) ]
```

Representative message types:

- **Teensy → Pi**: `TAP_EVENT`, `RIM_UPDATE`, `HEARTBEAT`, `DIAGNOSTIC`
- **Pi → Teensy**: `RGB_FRAME`, `CONFIG`, `PING`, `BRIGHTNESS`, `CALIBRATION`

The `RGB_FRAME` payload is simply the packed RGB array for all LEDs. Audio no longer travels in the frame packet because audio synthesis now lives entirely on the Pi.

### Java game loop on Pi

The game loop runs as a Java process using a fixed-timestep loop at 30–60fps. Java is preferred over Python for the game loop to avoid virtual environment complexity and for stronger typing and performance predictability.

Key implementation notes:

- Use **OpenJDK 21** (LTS, available on Pi 5 ARM64 via `apt`) or **GraalVM CE for AArch64** for native compilation if GC pauses become an issue.
- Use **ZGC or Shenandoah GC** (available in OpenJDK 17+) for sub-millisecond GC pause targets in the game loop hot path.
- Pre-allocate all frame buffers and SDF parameter objects at startup. Avoid heap allocation in the render loop.
- Use **jSerialComm** for USB serial communication with the Teensy via `/dev/ttyACM0`.
- The game loop ticks on `System.nanoTime()` for stable frame timing independent of system load.

CV and voice processing remain in Python (MediaPipe and Porcupine have no Java SDKs). The Python process communicates with the Java game loop via a local Unix domain socket or named pipe — low-latency IPC with no network overhead.

### Pi-side software architecture

```
┌───────────────────────────────────────────┐
│  Java process (game loop, 30–60fps)      │
│                                           │
│  • SDF renderPixel() for all 900 LEDs    │
│  • Game state: fish AI, Pong physics,    │
│    Simon logic, handpan note zones       │
│  • Impact interpretation from feature    │
│    vectors received from Teensy          │
│  • Audio synthesis / OSC to SuperCollider│
│  • Serialize RGB_FRAME packets           │
│  • jSerialComm USB serial I/O to Teensy  │
│  • Receives CV + voice events via socket │
├───────────────────────────────────────────┤
│  Python process (CV + voice)             │
│                                           │
│  • MediaPipe hand tracking + fisheye CV  │
│  • Porcupine wake word + Rhino intent    │
│  • Whisper STT / LLM API calls           │
│  • Publishes events to Java via socket   │
└───────────────────────────────────────────┘
              │ USB serial (CDC ACM)
              │ ↑ { x, y, rise_time, peak_amp,
              │    spectral_centroid, decay_tc,
              │    hf_ratio, rim_angle }
              │ ↓ RGB_FRAME / CONFIG / PING
┌───────────────────────────────────────────┐
│  Teensy 4.0                              │
│                                           │
│  • Piezo ADC + TDOA localization         │
│  • Acoustic feature extraction           │
│  • Rim position ADC                      │
│  • Framed USB serial protocol            │
│  • Raw RGB frame → FastLED DMA → LEDs    │
└───────────────────────────────────────────┘
```

## Impact type classification

The piezo ADC signal contains more information than tap position alone. The waveform shape, frequency content, and envelope of an impact are distinct for different strike types, enabling the Teensy to classify what struck the surface in addition to where. This enriches the handpan mode significantly — the same position can produce different sounds depending on playing technique, approximating the behavior of a real acoustic handpan.

### What the signal reveals

Each impact type produces a characteristic acoustic signature:

| Strike type | Rise time | Amplitude | Spectral character | Decay |
|---|---|---|---|---|
| Fingertip tap | Fast (~0.5 ms) | Medium | Mid-frequency dominant | Short |
| Knuckle rap | Very fast | Medium | High-frequency, sharp transient | Very short |
| Palm edge (tak) | Medium (~1–2 ms) | High | Low-frequency dominant | Short |
| Full palm (gu) | Slow (>2 ms) | Very high | Very low frequency | Long |
| Soft fingertip | Slow | Low | Broad, diffuse | Short |
| Mallet | Very fast | Variable | Narrow band, very clean | Long sustain |

### Feature extraction

After a tap threshold is crossed, the Teensy captures a 512–1024 sample window at ~96 kHz via ADC DMA (~5–10 ms). Five lightweight features are extracted from this window:

- **Rise time**: time from threshold crossing to peak amplitude
- **Peak amplitude**: overall strike force / velocity
- **Spectral centroid**: center of mass of frequency content, estimated via Goertzel algorithm on 4–6 frequency bands (~50 µs compute)
- **Decay time constant**: rate of envelope fall-off after peak
- **High-frequency energy ratio**: energy above ~5 kHz as a fraction of total energy

Total feature extraction time is well under 1 ms after the capture window closes.

### Classification

Two options, in order of implementation complexity:

**Threshold rule tree (start here)**: A small hand-coded decision tree on the five features. Fast, deterministic, easy to tune on the physical table. Suitable for a vocabulary of 4–6 distinct strike types.

**k-NN classifier with calibration mode**: Record labeled feature vectors for each strike type during a calibration session. Classify new taps by nearest neighbor in 5D feature space. Naturally adapts to different players' hands and mallets. Feature vectors and class labels are stored on the Pi; the Teensy loads them at boot via USB serial. Different player profiles can be saved and selected.

### Handpan sound mapping

Impact type selects a synthesis patch on the Teensy Audio Library, not just a note. The same tap position plays the same pitch but with completely different timbre:

| Strike type | Handpan technique | Synthesis patch |
|---|---|---|
| Fingertip tap | Standard tone field strike | Clean fundamental + harmonics, moderate sustain, medium reverb |
| Soft fingertip | Ghosted / dampened note | Soft attack, short sustain, low amplitude |
| Knuckle | Percussive ghost note | Short, breathy, high harmonic content, fast decay |
| Palm edge | Tak — muted bass slap | Deep thud, low fundamental, minimal sustain |
| Full palm | Gu — center bass technique | Deep resonant bass, long sustain, heavy reverb |
| Mallet | Soft mallet on tone field | Very pure sine-like tone, long sustain, minimal attack transient |

### Generalization to other modes

Impact type enriches every interaction mode, not just handpan:

- **Simon game**: palm slap could trigger a wrong-answer buzz; fingertip is normal selection
- **Pong**: mallet strike adds maximum power to the ball; fingertip is a normal hit; palm slap adds spin
- **Fish animations**: mallet strike summons a whale; fingertip gently disturbs a school; palm slap scatters fish violently
- **Generative soundscape**: the table becomes a full percussion instrument — different strike types across different zones produce a rich layered composition

### Calibration workflow

A built-in calibration mode prompts the user to strike the table in specific ways and records feature vectors with labels. Since the glass-pebble-over-plexiglass surface has unusual acoustic properties, per-table calibration is recommended over fixed factory thresholds. Calibration profiles are stored on the Pi and loaded to the Teensy at startup, allowing multiple player profiles to be saved and selected via voice command or rim gesture.
