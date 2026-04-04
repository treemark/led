// Moon phase animation v3 for Pixelblaze
// Two-circle crescent with exaggeration, new-moon ring, and smooth transition

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

// New-moon ring settings
var RING_INNER = 0.36
var RING_SMOOTHNESS = 0.08
var RING_DIM = 0.15

// Transition zone: crescent fades out over this phase range near new moon
var TRANSITION_ZONE = 0.12

var width = 16;
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

// Transition blend factors, computed once per frame
var gCrescentFade = 1
var gRingFade = 0

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
    precomputeTransition()
    updateMoonPixels()
}

function precomputeTerminator() {
    var phase01 = moonPhase
    var exaggeratedPhase = clamp(pow(phase01, INV_EXAGGERATION), 0, 1)

    gMx = 0.5 * cos(PI * exaggeratedPhase)
    gWaning = moonPhaseFull >= 1

    var absMx = abs(gMx)
    gUseCircle = absMx > 0.01

    gCx = 0
    gR = 0
    gSignDir = 1
    if (gUseCircle) {
        gCx = (gMx * gMx - 0.25) / (2 * gMx)
        gR = abs(gMx - gCx)
        gSignDir = gMx > gCx ? 1 : -1
    }
}

function precomputeTransition() {
    // Distance from new moon: 0 at new moon, 1 at full moon
    var distFromNew = min(moonPhaseFull, 2 - moonPhaseFull)

    // Crescent fades out smoothly near new moon
    gCrescentFade = clamp(distFromNew / TRANSITION_ZONE, 0, 1)
    // Smooth the fade with ease curve
    gCrescentFade = gCrescentFade * gCrescentFade * (3 - 2 * gCrescentFade)

    // Ring fades in as crescent fades out
    gRingFade = 1 - gCrescentFade
}

function updateMoonPixels() {
    for (var y = 0; y < resolution; y++) {
        for (var x = 0; x < resolution; x++) {
            var index = y * resolution + x
            var dx = (x / resolutionMinusOne) - 0.5
            var dy = (y / resolutionMinusOne) - 0.5
            var brightness = calculateMoonBrightness(dx, dy)
            moonPixels[index] = brightness
        }
    }
}

function calculateMoonBrightness(dx, dy) {
    var dist = sqrt(dx * dx + dy * dy)
    var moonEdge = smoothstepDown(dist, 0.5, EDGE_SMOOTHNESS)

    // Crescent brightness from terminator
    var crescent = calculateTerminator(dx, dy) * gCrescentFade

    // Ring: lit annulus near moon edge, only visible near new moon
    var ringInner = smoothstepUp(dist, RING_INNER, RING_SMOOTHNESS)
    var ring = moonEdge * ringInner * gRingFade * RING_DIM

    // Combine: crescent dominates when visible, ring shows at new moon
    var combined = crescent * moonEdge + ring
    return min(combined, 1)
}

function calculateTerminator(dx, dy) {
    if (gUseCircle == 0) {
        return gWaning ? smoothstepDown(dx, gMx, TERMINATOR_SMOOTHNESS) : smoothstepUp(dx, gMx, TERMINATOR_SMOOTHNESS)
    }

    var underSqrt = gR * gR - dy * dy
    if (underSqrt < 0) {
        return gWaning ? (dx < gCx ? 1 : 0) : (dx > gCx ? 1 : 0)
    }

    var xTerm = gCx + gSignDir * sqrt(underSqrt)
    return gWaning ? smoothstepDown(dx, xTerm, TERMINATOR_SMOOTHNESS) : smoothstepUp(dx, xTerm, TERMINATOR_SMOOTHNESS)
}

export function render2D(index, x, y) {
    var altx = altPixelMap[floor(index)][0];
    var alty = altPixelMap[floor(index)][1];

    var pixelX = floor(x * resolution)
    var pixelY = floor(y * resolution)

    var brightness = moonPixels[pixelY * resolution + pixelX]
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
