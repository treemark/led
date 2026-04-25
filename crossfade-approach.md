# Pixelblaze Pattern Crossfade — On-Device Combo Pattern with Blend Variable

## Overview

This approach creates a **single Pixelblaze pattern** that embeds the rendering logic of multiple animations and exposes a `blend` variable controllable via WebSocket `setVars`. The Pixelblaze computes both patterns every frame, mixes their colors per-pixel, and outputs the blended result — all at the device's native frame rate with zero network latency during rendering.

## Why This Approach

- **True pixel-level crossfade** — no blackout between patterns
- **Runs at native Pixelblaze frame rate** — no network latency in the render loop
- **Already proven infrastructure** — your `PixelblazeController.java` already supports `setVars` and your `pixelblaze_pattern.js` already demonstrates exported variable control
- **Both target patterns are `render2D` + `hsv()`** — structurally compatible

## Core Concept

Pixelblaze's `hsv()` and `rgb()` are *output* calls — you can only call one per pixel per frame. To crossfade, you must:

1. Refactor each pattern so its rendering logic **returns** color values instead of calling `hsv()` directly
2. Compute both patterns' colors for each pixel
3. Blend the results
4. Make a single `hsv()` or `rgb()` call with the blended color

## Architecture

```
┌─────────────────────────────────────────────────┐
│  Pixelblaze Device (combo-crossfade.js)         │
│                                                 │
│  export var blend = 0.0   ◄── setVars from Java │
│                                                 │
│  beforeRender(delta):                           │
│    ├── updatePatternA(delta)                    │
│    └── updatePatternB(delta)                    │
│                                                 │
│  render2D(index, x, y):                         │
│    ├── [hA, sA, vA] = computePatternA(x, y)    │
│    ├── [hB, sB, vB] = computePatternB(x, y)    │
│    ├── mix colors using blend factor            │
│    └── hsv(hMix, sMix, vMix)                    │
│                                                 │
└─────────────────────────────────────────────────┘
         ▲
         │  WebSocket setVars: {"setVars":{"blend":0.35}}
         │
┌────────┴────────────────────────────────────────┐
│  Java Controller (PixelblazeController.java)    │
│                                                 │
│  crossfadeTo(duration):                         │
│    for t = 0.0 → 1.0:                           │
│      setVariable("blend", t)                    │
│      sleep(stepInterval)                        │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Step 1: Refactor Patterns Into Pure Functions

Each pattern needs its rendering logic extracted into a function that **returns** color components rather than calling `hsv()` directly.

### Original moon-phases-v6.js render:

```javascript
export function render2D(index, x, y) {
    var pixelX = floor(x * resolution)
    var pixelY = floor(y * resolution)
    var brightness = moonPixels[pixelY * resolution + pixelX] * gDimFactor
    hsv(0.1, 0.1, brightness)  // ← direct output, can't blend
}
```

### Refactored to return values:

```javascript
function moonColor(x, y) {
    var pixelX = floor(x * resolution)
    var pixelY = floor(y * resolution)
    var brightness = moonPixels[pixelY * resolution + pixelX] * gDimFactor
    return [0.1, 0.1, brightness]  // [h, s, v] — no hsv() call
}
```

### Original 2d-pulse.js render:

```javascript
export function render2D(index, x, y) {
    h = (1 + sin(x * z + t1) + cos(y * z + t2)) * 0.5
    v = h
    v = v * v * v / 2
    hsv(h, 1, v)  // ← direct output
}
```

### Refactored:

```javascript
function pulseColor(x, y) {
    var h = (1 + sin(x * z + t1) + cos(y * z + t2)) * 0.5
    var v = h
    v = v * v * v / 2
    return [h, 1, v]  // [h, s, v]
}
```

## Step 2: Color Blending Strategy

### Option A: Blend in RGB Space (Recommended)

HSV blending can produce unexpected intermediate hues (e.g., blending red h=0 with cyan h=0.5 passes through green). RGB blending is perceptually more predictable.

Pixelblaze doesn't have built-in HSV→RGB conversion you can intercept, so you implement it:

```javascript
// Attempt a simplified HSV to RGB conversion in Pixelblaze's language
function hsv2rgb(h, s, v) {
    h = h - floor(h)  // wrap to 0..1
    var i = floor(h * 6)
    var f = h * 6 - i
    var p = v * (1 - s)
    var q = v * (1 - f * s)
    var t = v * (1 - (1 - f) * s)

    var r, g, b
    if (i == 0) { r = v; g = t; b = p }
    else if (i == 1) { r = q; g = v; b = p }
    else if (i == 2) { r = p; g = v; b = t }
    else if (i == 3) { r = p; g = q; b = v }
    else if (i == 4) { r = t; g = p; b = v }
    else             { r = v; g = p; b = q }

    return [r, g, b]
}
```

Then blend:

```javascript
function blendAndOutput(hsvA, hsvB, blend) {
    var rgbA = hsv2rgb(hsvA[0], hsvA[1], hsvA[2])
    var rgbB = hsv2rgb(hsvB[0], hsvB[1], hsvB[2])

    var r = rgbA[0] * (1 - blend) + rgbB[0] * blend
    var g = rgbA[1] * (1 - blend) + rgbB[1] * blend
    var b = rgbA[2] * (1 - blend) + rgbB[2] * blend

    rgb(r, g, b)
}
```

### Option B: Blend in HSV Space (Simpler, Sometimes Sufficient)

If the two patterns have similar hue ranges or you want artistic intermediate colors:

```javascript
var h = hsvA[0] * (1 - blend) + hsvB[0] * blend
var s = hsvA[1] * (1 - blend) + hsvB[1] * blend
var v = hsvA[2] * (1 - blend) + hsvB[2] * blend
hsv(h, s, v)
```

This is simpler but can produce unexpected hue transitions. For moon (warm amber, h≈0.1) to pulse (rainbow), RGB blending will look cleaner.

### Option C: Brightness-Only Crossfade (Simplest)

If you just want to fade one out and the other in without mixing colors:

```javascript
var vA = hsvA[2] * (1 - blend)
var vB = hsvB[2] * blend

if (vA > vB) {
    hsv(hsvA[0], hsvA[1], vA)
} else {
    hsv(hsvB[0], hsvB[1], vB)
}
```

This produces a "winner-takes-all per pixel" effect that looks like a dissolve.

## Step 3: The Combo Pattern

Here's the skeleton of the combined pattern file. This merges moon-phases-v6 and 2d-pulse into a single file with crossfade control:

```javascript
// crossfade-moon-pulse.js
// Combo pattern: crossfades between Moon Phases and 2D Pulse
// Control via WebSocket: {"setVars":{"blend":0.5}}
//   blend = 0.0 → 100% Moon
//   blend = 1.0 → 100% Pulse

export var blend = 0  // 0 = moon, 1 = pulse

// ============================================================
// PATTERN A: Moon Phases (all state/functions from moon-phases-v6.js)
// ============================================================
// ... (paste all moon-phases-v6 state variables here, with the
//      export function beforeRender/render2D renamed) ...

// Key change: rename beforeRender → moonBeforeRender
function moonBeforeRender(delta) {
    // ... (exact copy of moon-phases-v6 beforeRender body) ...
}

// Key change: return [h, s, v] instead of calling hsv()
function moonColor(index, x, y) {
    var pixelX = floor(x * resolution)
    var pixelY = floor(y * resolution)
    var brightness = moonPixels[pixelY * resolution + pixelX] * gDimFactor
    return [0.1, 0.1, brightness]
}

// ============================================================
// PATTERN B: 2D Pulse (all state/functions from 2d pulse.js)
// ============================================================
var pulse_t1, pulse_t2, pulse_z

function pulseBeforeRender(delta) {
    pulse_t1 = time(3.3 / 65.536) * PI2
    pulse_t2 = time(6.0 / 65.536) * PI2
    pulse_z = 1 + wave(time(13 / 65.536)) * 5
}

function pulseColor(index, x, y) {
    var h = (1 + sin(x * pulse_z + pulse_t1) + cos(y * pulse_z + pulse_t2)) * 0.5
    var v = h
    v = v * v * v / 2
    return [h, 1, v]
}

// ============================================================
// HSV → RGB conversion for blending in RGB space
// ============================================================
function hsv2rgb(h, s, v) {
    h = h - floor(h)
    var i = floor(h * 6)
    var f = h * 6 - i
    var p = v * (1 - s)
    var q = v * (1 - f * s)
    var t2 = v * (1 - (1 - f) * s)
    var r, g, b
    if (i == 0)      { r = v;  g = t2; b = p }
    else if (i == 1) { r = q;  g = v;  b = p }
    else if (i == 2) { r = p;  g = v;  b = t2 }
    else if (i == 3) { r = p;  g = q;  b = v }
    else if (i == 4) { r = t2; g = p;  b = v }
    else             { r = v;  g = p;  b = q }
    return [r, g, b]
}

// ============================================================
// MAIN: Crossfade orchestration
// ============================================================
export function beforeRender(delta) {
    moonBeforeRender(delta)
    pulseBeforeRender(delta)
}

export function render2D(index, x, y) {
    // Early-out optimizations: skip computation if fully on one pattern
    if (blend <= 0) {
        var mc = moonColor(index, x, y)
        hsv(mc[0], mc[1], mc[2])
        return
    }
    if (blend >= 1) {
        var pc = pulseColor(index, x, y)
        hsv(pc[0], pc[1], pc[2])
        return
    }

    // Compute both
    var mc = moonColor(index, x, y)
    var pc = pulseColor(index, x, y)

    // Blend in RGB space
    var rgbA = hsv2rgb(mc[0], mc[1], mc[2])
    var rgbB = hsv2rgb(pc[0], pc[1], pc[2])

    var r = rgbA[0] * (1 - blend) + rgbB[0] * blend
    var g = rgbA[1] * (1 - blend) + rgbB[1] * blend
    var b = rgbA[2] * (1 - blend) + rgbB[2] * blend

    rgb(r, g, b)
}

export function render(index) {
    // 1D fallback
    render2D(index, index / pixelCount, 0)
}
```

## Step 4: Java Controller — Animate the Crossfade

Add this method to `PixelblazeController.java`:

```java
/**
 * Smoothly crossfade between pattern A and pattern B.
 * Requires the "crossfade combo" pattern to be active on the Pixelblaze.
 *
 * @param targetBlend  target blend value (0.0 = pattern A, 1.0 = pattern B)
 * @param durationMs   crossfade duration in milliseconds
 * @param steps        number of intermediate steps (50-100 is smooth)
 */
public void animateCrossfade(double targetBlend, int durationMs, int steps) {
    Thread fadeThread = new Thread(() -> {
        try {
            // Read current blend (assume 0 if unknown; could query via getVars)
            double currentBlend = 0.0;  // Or track this as instance state
            double stepSize = (targetBlend - currentBlend) / steps;
            int stepDelay = durationMs / steps;

            for (int i = 0; i <= steps; i++) {
                double blend = currentBlend + stepSize * i;
                blend = Math.max(0.0, Math.min(1.0, blend));
                setVariable("blend", blend);
                Thread.sleep(stepDelay);
            }

            // Ensure we land exactly on target
            setVariable("blend", targetBlend);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }, "Crossfade-Animator");
    fadeThread.setDaemon(true);
    fadeThread.start();
}
```

Usage:

```java
// Crossfade from moon to pulse over 3 seconds
controller.animateCrossfade(1.0, 3000, 60);

// Later, crossfade back to moon over 5 seconds
controller.animateCrossfade(0.0, 5000, 100);
```

## Step 5: Scaling to N Patterns

To support more than two patterns, extend the combo pattern with a pattern selector:

```javascript
export var blend = 0         // 0.0 to 1.0
export var patternA = 0      // 0 = moon, 1 = pulse, 2 = coronal, ...
export var patternB = 1

function getPatternColor(patternId, index, x, y) {
    if (patternId == 0) return moonColor(index, x, y)
    if (patternId == 1) return pulseColor(index, x, y)
    if (patternId == 2) return coronalColor(index, x, y)
    // ... add more patterns
    return [0, 0, 0]
}

export function render2D(index, x, y) {
    var cA = getPatternColor(patternA, index, x, y)
    var cB = getPatternColor(patternB, index, x, y)
    // ... blend as before
}
```

From Java:
```java
controller.setVariable("patternA", 0);  // moon
controller.setVariable("patternB", 2);  // coronal mass ejection
controller.animateCrossfade(1.0, 3000, 60);
```

## Performance Considerations

| Concern | Mitigation |
|---------|-----------|
| Running two patterns per frame doubles computation | Use early-out when `blend` is 0 or 1; Pixelblaze V3 handles this well for most patterns |
| HSV→RGB conversion per pixel adds overhead | Only needed during active crossfade; skip when blend is 0 or 1 |
| Array allocations (`return [h, s, v]`) | Pixelblaze optimizes small arrays; alternatively use global scratch variables |
| Memory for multiple patterns' state | Pixelblaze V3 has ~20KB for patterns; keep combined state under this limit |
| `setVars` WebSocket latency | 50ms steps are fine; the Pixelblaze interpolates smoothly between updates |

## Optimization: Global Scratch Variables

To avoid array allocation per pixel, use global scratch variables:

```javascript
var _rA, _gA, _bA, _rB, _gB, _bB

// Instead of returning arrays:
function moonColorInto_A(x, y) {
    var brightness = moonPixels[...] * gDimFactor
    // Write directly to scratch globals
    hsv2rgb_A(0.1, 0.1, brightness)  // sets _rA, _gA, _bA
}

function hsv2rgb_A(h, s, v) {
    // ... same conversion but writes to _rA, _gA, _bA
}
```

This eliminates all array allocation in the hot loop.

## Summary

| Step | What | Where |
|------|------|-------|
| 1 | Refactor each pattern's render into a pure function returning `[h, s, v]` | .js pattern files |
| 2 | Add HSV→RGB conversion | combo .js file |
| 3 | Create combo pattern with `blend` variable and dual-render + mix | New .js on Pixelblaze |
| 4 | Add `animateCrossfade()` method | PixelblazeController.java |
| 5 | (Optional) Add `patternA`/`patternB` selectors for N-pattern support | combo .js + Java |
