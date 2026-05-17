// Coronal Moon Ejection
// Combines moon-phases-v7.js with coronal mass ejection.js
// The moon is overlayed on the coronal mass ejection, scaled by coreSize

// ===== CORONAL MASS EJECTION VARIABLES =====
var coreSize = 0.1;
var c2 = coreSize / 4;
translate(-0.5,-0.5);
setPerlinWrap(6,256,256);

var density = 6;
export var densityPiConversion = 1/(3 * PI)
var gain = 0.25;
var iterations = 3;
var mirror = false
var cutoff = 0.675

// ===== CORONAL MASS EJECTION CONTROLS =====
export function sliderDensity(v) {
    density = 1 + floor(v*10);
}

export function showNumberDensity() {
    return density
}

export function toggleMirror(v) {
    mirror = v
}

export function sliderFractalGain(v) {
    gain = v
}

export function sliderIterations(v) {
    iterations = 1 + floor(v*5)
}

export function showNumberIterations() {
    return iterations
}

export function sliderCutoff(v) {
    cutoff = v
}

// ===== MOON PHASE VARIABLES =====
export var moonPhase = 0
export var moonPhaseFull = 0
export var debug = 0

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
var MIN_BRIGHTNESS = 0.005

// Max total brightness across all pixels (virtual 32x32 grid sum).
var MAX_TOTAL_BRIGHTNESS = 150

// Real moon phase settings
var SYNODIC_PERIOD = 29.53059
var CYCLE_INTERVAL = 10
var ANIMATION_DURATION = 15

var width = 64
var altPixelMap = array(pixelCount)
var moonPixels = array(resolution * resolution)
export var what = 0

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

// Animation state
var realMoonPhaseFull = 0
var animating = 0
var animTimer = 0
var cycleTimer = 0

// Coronal animation timers
var t1 = 0
var noiseTime = 0
var noiseYTime = 0

// ===== INITIALIZATION =====
function initPixelMap(pixelCount) {
    for (i = 0; i < pixelCount; i++) {
        y = floor(i / width)
        x = i % width
        x = y % 2 == 1 ? width - 1 - x : x
        altPixelMap[i] = [x, y]
    }
}
initPixelMap(pixelCount)

// ===== MOON PHASE CALCULATIONS =====
function daysSinceRefNewMoon() {
    var yr = clockYear() - 2000
    var leapDays = floor((yr + 3) / 4)
    var days = yr * 365 + leapDays

    var m = clockMonth()
    var md = 0
    if (m == 2) md = 31
    if (m == 3) md = 59
    if (m == 4) md = 90
    if (m == 5) md = 120
    if (m == 6) md = 151
    if (m == 7) md = 181
    if (m == 8) md = 212
    if (m == 9) md = 243
    if (m == 10) md = 273
    if (m == 11) md = 304
    if (m == 12) md = 334

    var isLeap = (clockYear() % 4 == 0)
    if (isLeap && m > 2) md += 1

    days += md + clockDay()
    days -= 6
    days += clockHour() / 24 + clockMinute() / 1440 + clockSecond() / 86400 - 0.76

    return days
}

function calculateRealMoonPhase() {
    var days = daysSinceRefNewMoon()
    var cycles = days / SYNODIC_PERIOD
    var phase01 = cycles - floor(cycles)
    if (phase01 < 0) phase01 += 1
    realMoonPhaseFull = phase01 * 2
}

function precomputeTerminator() {
    var phase01 = moonPhase
    var exaggeratedPhase = clamp(pow(phase01, INV_EXAGGERATION), 0, 1)

    var distFromNew = min(moonPhaseFull, 2 - moonPhaseFull)
    var newMoonProx = 1 - clamp(distFromNew / 0.2, 0, 1)
    var gap = MAX_GAP * newMoonProx

    var fadeT = clamp(distFromNew / FADE_ZONE, 0, 1)
    gCrescentFade = fadeT * fadeT * (3 - 2 * fadeT)

    var distFromFull = abs(moonPhaseFull - 1)
    var fullFadeT = clamp(distFromFull / FULL_FADE_ZONE, 0, 1)
    gFullMoonFade = fullFadeT * fullFadeT * (3 - 2 * fullFadeT)

    var edge = 0.5 - gap
    gMx = edge * cos(PI * exaggeratedPhase)
    gMx = min(gMx, edge)

    gWaning = moonPhaseFull >= 1

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

    var lit = MIN_BRIGHTNESS + terminator * (1 - MIN_BRIGHTNESS)

    return moonEdge * lit
}

function calculateTerminator(dx, dy) {
    if (gUseCircle == 0) {
        var halfResult = gWaning ? smoothstepDown(dx, gMx, TERMINATOR_SMOOTHNESS) : smoothstepUp(dx, gMx, TERMINATOR_SMOOTHNESS)
        return halfResult * gFullMoonFade + (1 - gFullMoonFade)
    }

    var ddx = dx - gCx
    var distToC2 = sqrt(ddx * ddx + dy * dy)
    var radial = smoothstepUp(distToC2, gR, TERMINATOR_SMOOTHNESS)

    var underSqrt = gR * gR - dy * dy
    if (underSqrt < 0) {
        return 1
    }

    var xTerm = gCx + gSignDir * sqrt(underSqrt)
    var directional = gWaning ? smoothstepDown(dx, xTerm, TERMINATOR_SMOOTHNESS) : smoothstepUp(dx, xTerm, TERMINATOR_SMOOTHNESS)

    var result = directional * gCrescentFade + radial * (1 - gCrescentFade)

    return result * gFullMoonFade + (1 - gFullMoonFade)
}

// ===== UTILITY FUNCTIONS =====
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

// ===== BEFORE RENDER =====
export function beforeRender(delta) {
    var deltaSec = delta / 1000

    // Update coronal animation timers
    t1 = time(.2);
    noiseTime = time(10) * 256;
    noiseYTime = time(8) * 256;
    setPerlinWrap(density,256,256);
    densityPiConversion = 1/PI * density * (mirror ? 1 : .5)

    // Update moon phase
    calculateRealMoonPhase()

    if (animating) {
        animTimer += deltaSec
        if (animTimer >= ANIMATION_DURATION) {
            animating = 0
            animTimer = 0
            cycleTimer = 0
            moonPhaseFull = realMoonPhaseFull
        } else {
            var progress = animTimer / ANIMATION_DURATION
            var eased = progress * progress * (3 - 2 * progress)
            moonPhaseFull = (realMoonPhaseFull + eased * 2) % 2
        }
    } else {
        moonPhaseFull = realMoonPhaseFull
        cycleTimer += deltaSec
        if (cycleTimer >= CYCLE_INTERVAL) {
            animating = 1
            animTimer = 0
        }
    }

    moonPhase = moonPhaseFull % 1
    precomputeTerminator()
    updateMoonPixels()
}

// ===== RENDER2D =====
export function render2D(index, x, y) {
    // Calculate distance from center for moon overlay detection
    var distFromCenter = hypot(x, y)
    var moonRadius = coreSize / 2
    
    // Check if this pixel is within the moon's area
    if (distFromCenter <= moonRadius) {
        // Map to moon coordinate space (0 to 1)
        // Scale x, y to fit within the moon's radius
        var moonX = (x / moonRadius + 1) / 2  // Convert from -moonRadius..moonRadius to 0..1
        var moonY = (y / moonRadius + 1) / 2
        
        // Clamp to valid range
        moonX = clamp(moonX, 0, 1)
        moonY = clamp(moonY, 0, 1)
        
        // Sample moon texture
        var pixelX = floor(moonX * resolution)
        var pixelY = floor(moonY * resolution)
        pixelX = clamp(pixelX, 0, resolution - 1)
        pixelY = clamp(pixelY, 0, resolution - 1)
        
        var moonBrightness = moonPixels[pixelY * resolution + pixelX] * gDimFactor
        
        // Render moon: yellowish/white color
        hsv(0.1, 0.1, moonBrightness)
    } else {
        // Render coronal mass ejection
        // Convert to radial coords for corona effect
        var coronaX = x
        var coronaY = y
        var tmp = hypot(coronaX, coronaY)
        coronaX = atan2(coronaY, coronaX) * densityPiConversion
        coronaY = tmp

        // Generate noise field
        var v = 1 - perlinTurbulence(coronaX, coronaY - noiseYTime, noiseTime, 2, gain, iterations)

        // Convert noise field to discrete radial "flares"
        v = max(smoothstep(cutoff, 1, v), (1 - ((coronaY * v) - c2) / coreSize))
        v = v * v * v

        // Draw star + stellar flares
        hsv(t1 - (0.125 * v), 6.5 * coronaY - v, v)
    }
}
