export function beforeRender(delta) {
    var t1 = time(.1)
}

export function render(index) {
    var h = t1 + index/pixelCount
    var s = 1
    var v = 1
    hsv(h, s, v)
}

// Moon phase animation v6 for Pixelblaze
// New: minimum brightness for dark/shadow pixels on the moon disc
// Fix: full moon crossfade hides the terminator direction flip
// Two-circle crescent with exaggeration, natural ring at new moon,
// and global brightness limiting to prevent voltage drop

export var moonPhase = 0
export var moonPhaseFull = 0
export var debug = 0

var phaseSpeed = 0.01
var resolution = 32
var resolutionMinusOne = resolution - 1

var EDGE_SMOOTHNESS = 0.15
var TERMINATOR_SMOOTHNESS = 0.15

// Exaggeration: 1.0 = normal, higher = more exaggerated crescent
var EXAGGERATION = 1.4
var INV_EXAGGERATION = 1 / 1.4

// Max gap at new moon: terminator circle shrinks by this much
var MAX_GAP = 0.00005

// Crossfade zone near new moon to smooth the left-right flip
var FADE_ZONE = 0.08

// Crossfade zone near full moon to hide the waxing-to-waning terminator flip
var FULL_FADE_ZONE = 0.05

// Minimum brightness for dark/shadow pixels on the moon disc.
// 0 = fully dark shadows (original behavior), 1 = no shadows at all.
// Try values like 0.05–0.15 for a subtle lunar glow in the shadow region.
var MIN_BRIGHTNESS = 0.01

// Max total brightness across all pixels (virtual 32x32 grid sum).
// A full moon sums to ~800 on the virtual grid.
// Physical LEDs are roughly 1/4 of virtual pixels.
// Lower this value if you see voltage drop / flickering.
var MAX_TOTAL_BRIGHTNESS = 250

var width = 64;
var altPixelMap = array(pixelCount)
var moonPixels = array(resolution * resolution)
export var what = 0;

// Terminator circle params, computed once per frame
var gMx = 0
var gCx = 0
var gR = 0
var gSignDir = 1
var gUseCircle = 0
var gWaning = 0
var gCrescentFade = 1
var gFullMoonFade = 1

// Brightness limiter
var gDimFactor = 1
var gTotalBrightness = 0

function initPixelMap(pixelCount) {
    for (i = 0; i < pixelCount; i++) {
        y = floor(i / width)
        x = i % width
        x = y % 2 == 1 ? width - 1 - x : x
        altPixelMap[i] = [x, y];
    }
}
initPixelMap(pixelCount)

export function beforeRender(delta) {
    moonPhaseFull = (moonPhaseFull + phaseSpeed) % 2
    moonPhase = moonPhaseFull % 1
    precomputeTerminator()
    updateMoonPixels()
}

function precomputeTerminator() {
    var phase01 = moonPhase
    var exaggeratedPhase = clamp(pow(phase01, INV_EXAGGERATION), 0, 1)

    // Distance from new moon: 0 at new, 1 at full
    var distFromNew = min(moonPhaseFull, 2 - moonPhaseFull)

    // Gap grows as we approach new moon
    var newMoonProx = 1 - clamp(distFromNew / 0.2, 0, 1)
    var gap = MAX_GAP * newMoonProx

    // Crossfade to smooth the waning-to-waxing directional flip near new moon
    var fadeT = clamp(distFromNew / FADE_ZONE, 0, 1)
    gCrescentFade = fadeT * fadeT * (3 - 2 * fadeT)

    // FIX: Crossfade near full moon to hide the terminator direction flip.
    // At full moon, the terminator jumps from far-left to far-right, but
    // blending toward 1.0 (fully lit) makes this invisible.
    var distFromFull = abs(moonPhaseFull - 1)
    var fullFadeT = clamp(distFromFull / FULL_FADE_ZONE, 0, 1)
    gFullMoonFade = fullFadeT * fullFadeT * (3 - 2 * fullFadeT)

    // Edge of terminator circle: shrunk by gap near new moon
    var edge = 0.5 - gap

    // mx: +edge at new moon, 0 at half, -edge at full
    gMx = edge * cos(PI * exaggeratedPhase)

    // Cap mx so terminator never exceeds the shrunk edge
    gMx = min(gMx, edge)

    gWaning = moonPhaseFull >= 1

    // Circle through 3 points: (0, -edge), (mx, 0), (0, edge)
    var absMx = abs(gMx)
    gUseCircle = absMx > 0.01

    gCx = 0
    gR = 0
    gSignDir = 1
    if (gUseCircle) {
        gCx = (gMx * gMx - edge * edge) / (2 * gMx)
        gR = abs(gMx - gCx)
        gSignDir = gMx > gCx ? 1 : -1
    }
}

function updateMoonPixels() {
    gTotalBrightness = 0
    for (var y = 0; y < resolution; y++) {
        for (var x = 0; x < resolution; x++) {
            var index = y * resolution + x
            var dx = (x / resolutionMinusOne) - 0.5
            var dy = (y / resolutionMinusOne) - 0.5
            var brightness = calculateMoonBrightness(dx, dy)
            moonPixels[index] = brightness
            gTotalBrightness = gTotalBrightness + brightness
        }
    }

    // Compute global dimming factor
    if (gTotalBrightness > MAX_TOTAL_BRIGHTNESS) {
        gDimFactor = MAX_TOTAL_BRIGHTNESS / gTotalBrightness
    } else {
        gDimFactor = 1
    }
}

function calculateMoonBrightness(dx, dy) {
    var dist = sqrt(dx * dx + dy * dy)
    var moonEdge = smoothstepDown(dist, 0.5, EDGE_SMOOTHNESS)

    var terminator = calculateTerminator(dx, dy)

    // Apply minimum brightness: remap terminator from [0,1] to [MIN_BRIGHTNESS,1]
    // only for pixels on the moon disc (moonEdge > 0)
    var lit = MIN_BRIGHTNESS + terminator * (1 - MIN_BRIGHTNESS)

    return moonEdge * lit
}

function calculateTerminator(dx, dy) {
    // Near half moon, circle is too large; use vertical line
    if (gUseCircle == 0) {
        var halfResult = gWaning ? smoothstepDown(dx, gMx, TERMINATOR_SMOOTHNESS) : smoothstepUp(dx, gMx, TERMINATOR_SMOOTHNESS)
        return halfResult * gFullMoonFade + (1 - gFullMoonFade)
    }

    // Radial ring: lit when outside the terminator circle (non-directional)
    var ddx = dx - gCx
    var distToC2 = sqrt(ddx * ddx + dy * dy)
    var radial = smoothstepUp(distToC2, gR, TERMINATOR_SMOOTHNESS)

    var underSqrt = gR * gR - dy * dy
    if (underSqrt < 0) {
        // Polar caps: outside terminator arc, part of ring
        return 1
    }

    var xTerm = gCx + gSignDir * sqrt(underSqrt)
    var directional = gWaning ? smoothstepDown(dx, xTerm, TERMINATOR_SMOOTHNESS) : smoothstepUp(dx, xTerm, TERMINATOR_SMOOTHNESS)

    // Blend: directional crescent far from new moon, radial ring near new moon
    var result = directional * gCrescentFade + radial * (1 - gCrescentFade)

    // FIX: Near full moon, blend toward fully lit (1.0) to hide the terminator flip
    // gFullMoonFade is 0 at full moon, 1 away from full moon
    return result * gFullMoonFade + (1 - gFullMoonFade)
}

export function render2D(index, x, y) {
    var altx = altPixelMap[floor(index)][0];
    var alty = altPixelMap[floor(index)][1];

    var pixelX = floor(x * resolution)
    var pixelY = floor(y * resolution)

    var brightness = moonPixels[pixelY * resolution + pixelX] * gDimFactor
    hsv(0.1, 0.1, brightness)
}

function smoothstepDown(x, target, smoothness) {
    var halfSmooth = smoothness / 2
    if (x <= target - halfSmooth) return 1
    if (x >= target + halfSmooth) return 0
    var t = (x - (target - halfSmooth)) / smoothness
    return 1 - t * t * (3 - 2 * t)
}

function smoothstepUp(x, target, smoothness) {
    var halfSmooth = smoothness / 2
    if (x <= target - halfSmooth) return 0
    if (x >= target + halfSmooth) return 1
    var t = (x - (target - halfSmooth)) / smoothness
    return t * t * (3 - 2 * t)
}

function clamp(value, min2, max2) {
    return min(max(value, min2), max2)
}
