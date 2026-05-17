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
// New: real moon phase from date/time, periodic full-cycle animation
// New: minimum brightness for dark/shadow pixels on the moon disc
// Two-circle crescent with exaggeration, natural ring at new moon,
// and global brightness limiting to prevent voltage drop

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
// 0 = fully dark shadows, 1 = no shadows at all.
// Try 0.05-0.15 for a subtle lunar glow in the shadow region.
var MIN_BRIGHTNESS = 0.01

// Max total brightness across all pixels (virtual 32x32 grid sum).
var MAX_TOTAL_BRIGHTNESS = 250

// --- Real moon phase settings ---
// Synodic month (new moon to new moon) in days
var SYNODIC_PERIOD = 29.53059

// How often to run the full-cycle animation (seconds)
var CYCLE_INTERVAL = 120

// How long the animation takes (seconds)
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

function initPixelMap(pixelCount) {
    for (i = 0; i < pixelCount; i++) {
        y = floor(i / width)
        x = i % width
        x = y % 2 == 1 ? width - 1 - x : x
        altPixelMap[i] = [x, y]
    }
}
initPixelMap(pixelCount)

// Compute days since the reference new moon (Jan 6, 2000 18:14 UTC).
// Uses Pixelblaze clock functions. Accurate for years 2000-2099.
function daysSinceRefNewMoon() {
    var yr = clockYear() - 2000
    // Leap days for complete years since 2000 (works for 2000-2099)
    var leapDays = floor((yr + 3) / 4)
    var days = yr * 365 + leapDays

    // Cumulative days before current month (non-leap year)
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

    // Extra day if leap year and past February
    var isLeap = (clockYear() % 4 == 0)
    if (isLeap && m > 2) md += 1

    days += md + clockDay()

    // Subtract reference date (Jan 6 = day 6 of year 2000)
    days -= 6

    // Add fractional day for current time, subtract 0.76 for 18:14 UTC ref time
    days += clockHour() / 24 + clockMinute() / 1440 + clockSecond() / 86400 - 0.76

    return days
}

function calculateRealMoonPhase() {
    var days = daysSinceRefNewMoon()
    var cycles = days / SYNODIC_PERIOD
    // phase01: 0 = new moon, 0.5 = full moon, 1.0 = next new moon
    var phase01 = cycles - floor(cycles)
    if (phase01 < 0) phase01 += 1
    // Map to moonPhaseFull range [0, 2): 0=new, 1=full, 2=new
    realMoonPhaseFull = phase01 * 2
}

export function beforeRender(delta) {
    var deltaSec = delta / 1000

    calculateRealMoonPhase()

    if (animating) {
        animTimer += deltaSec
        if (animTimer >= ANIMATION_DURATION) {
            // Animation complete, snap back to real phase
            animating = 0
            animTimer = 0
            cycleTimer = 0
            moonPhaseFull = realMoonPhaseFull
        } else {
            // Sweep through full cycle starting and ending at real phase
            var progress = animTimer / ANIMATION_DURATION
            // Smooth ease-in-out
            var eased = progress * progress * (3 - 2 * progress)
            moonPhaseFull = (realMoonPhaseFull + eased * 2) % 2
        }
    } else {
        // Static: show real moon phase
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

    // Crossfade near full moon to hide the terminator direction flip
    var distFromFull = abs(moonPhaseFull - 1)
    var fullFadeT = clamp(distFromFull / FULL_FADE_ZONE, 0, 1)
    gFullMoonFade = fullFadeT * fullFadeT * (3 - 2 * fullFadeT)

    // Edge of terminator circle: shrunk by gap near new moon
    var edge = 0.5 - gap

    // mx: +edge at new moon, 0 at half, -edge at full
    gMx = edge * cos(PI * exaggeratedPhase)
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

export function render2D(index, x, y) {
    var altx = altPixelMap[floor(index)][0]
    var alty = altPixelMap[floor(index)][1]

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
