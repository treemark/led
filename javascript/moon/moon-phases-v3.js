// Moon phase animation library for Pixelblaze (curve-based approach with directional smoothstep)

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
// creating a natural thin ring from the crescent geometry
var MAX_GAP = 0.005

// Crossfade zone near new moon to smooth the left-right flip
var FADE_ZONE = 0.08

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
var gCrescentFade = 1

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

    // Crossfade to smooth the waning-to-waxing directional flip
    var fadeT = clamp(distFromNew / FADE_ZONE, 0, 1)
    gCrescentFade = fadeT * fadeT * (3 - 2 * fadeT)

    // Edge of terminator circle: shrunk by gap near new moon
    var edge = 0.5 - gap

    // mx: +edge at new moon, 0 at half, -0.5 at full
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

    var terminator = calculateTerminator(dx, dy)

    return moonEdge * terminator
}

function calculateTerminator(dx, dy) {
    // Near half moon, circle is too large; use vertical line
    if (gUseCircle == 0) {
        var halfResult = gWaning ? smoothstepDown(dx, gMx, TERMINATOR_SMOOTHNESS) : smoothstepUp(dx, gMx, TERMINATOR_SMOOTHNESS)
        return halfResult
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
    return directional * gCrescentFade + radial * (1 - gCrescentFade)
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
