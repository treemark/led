// Moon phase animation library for Pixelblaze (curve-based approach with directional smoothstep)

export var moonPhase = 0
export var moonPhaseFull = 0
export var debug=0;

var phaseSpeed = 0.01
var resolution = 32
var resolutionMinusOne = resolution - 1

// Global smoothness constants
var EDGE_SMOOTHNESS = 0.2
var TERMINATOR_SMOOTHNESS = 0.2

var width = 16;
var altPixelMap = array(pixelCount)
var moonPixels = array(resolution * resolution)
export var what = 0;

function initPixelMap(pixelCount) {
    // var map = array(pixelCount)
    // what=pixelCount;
    for (i = 0; i < pixelCount; i++) {
        y = floor(i / width)
        x = i % width
        x = y % 2 == 1 ? width - 1 - x : x //zigzag
        altPixelMap[i]=[x, y];
    }
    // pixelCoundDebug=altPixelMap[4];
}
initPixelMap(pixelCount)



export function beforeRender(delta) {
    moonPhaseFull = (moonPhaseFull + phaseSpeed) % 2
    // moonPhaseFull=0;
    moonPhase =moonPhaseFull % 1
    updateMoonPixels()
    // debug=pixelCount;
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
    // if (dx<=0)
    //   return 1;
    // else
    //   return 0;
    // TERMINATOR_SMOOTHNESS=.0;
    // moonPhase = 0
    // moonPhaseFull=1;
    var distance = sqrt(dx*dx + dy*dy)
    // moonPhaseFull=1.9;
    // Antialiasing for the moon's edge
    var edgeAntiAlias = smoothstepUp( 0.54,distance, EDGE_SMOOTHNESS)
    var terminatorEdge = .5;
    // Define three points for the curve
    var fudge = TERMINATOR_SMOOTHNESS*.7;
    var fudgeScaled = scale2(fudge,-fudge,moonPhase) ;
    var x1 = fudgeScaled;
    var y1 = -0.5
    var x2 =terminatorEdge * cos(PI * moonPhase) + -terminatorEdge * (moonPhase - 0.5) +fudgeScaled
    var y2 = 0
    var x3 =fudgeScaled
    var y3 = 0.5

    what = x2;
    // if (moonPhaseFull === 0) {
    //   return edgeAntiAlias; // Entire moon is fully illuminated
    // }


    // Calculate quadratic Bezier curve
    var t = (dy + 0.5) // Normalize dy to 0-1 range
    var x = (1-t)*(1-t)*x1 + 2*(1-t)*t*x2 + t*t*x3
    // Antialiasing for the terminator line
    var terminatorAntiAlias = moonPhaseFull>=1 ?
        smoothstepUp(dx, x, TERMINATOR_SMOOTHNESS)
        :smoothstepUp(x, dx, TERMINATOR_SMOOTHNESS)

    // terminatorAntiAlias = (dx>0)?0:1;

    // if (floor(moonPhaseFull)==0 )//&& floor(dy) ==1)
    //     debug = t;
    // terminatorAntiAlias=1;
    // edgeAntiAlias=1;
    // Combine edge and terminator antialiasing
    return edgeAntiAlias * terminatorAntiAlias
    // return edgeAntiAlias * terminatorAntiAlias

}

// export var minX=1.1;
// export var minY=1.1;
// export var maxX=0;
// export var maxY=0;

export function render2D(index, x, y) {
    // if (arrayLength(map) > 0)
    //   debug= index;
    // if (altPixelMap[index] == null)
    //   debug= index;
    // if (floor(index) < arrayLength(altPixelMap) && altPixelMap[floor(index)] != null)
    var altx = altPixelMap[floor(index)][0];
    var alty = altPixelMap[floor(index)][1];

    // var minX=min(minX,altx);
    //debug = min(index,debug);
    // var pixelX = (x < .5) ? ceil(x*resolution) : floor(x * resolution)
    // var pixelY = (y > .5) ? ceil(y*resolution) : floor(y * resolution)
    var pixelX = floor(x * resolution)
    var pixelY = floor(y * resolution)

    // if (x<minX)
    //   minX=x;


    // if (alty<minY)
    //   minY=alty;

    // if (y<pixelY)
    //   minY = pixelX;

    // if (pixelX == 0 && pixelY == 0) {
    //   return hsv(.7,1,1)
    // }

    // if (pixelX == 2 && pixelY == 2) {
    //   return hsv(.5,1,1)
    // }

    // if (pixelX == 15 && pixelY == 15) {
    //   return hsv(1,1,1)
    // }

    var brightness = moonPixels[pixelY * resolution + pixelX]
    hsv(0.1, 0.1, brightness)
}

function smoothstepUp(x, target, smoothness) {
    // return x>=target;
    if (x <= target) return 0
    if (x >= target + smoothness) return 1
    var t = (x - target) / smoothness
    return t * t * (3 - 2 * t)
}


function scale2(min, max, factor) {
    // Ensure factor is between 0 and 1
    factor = clamp(factor, 0, 1)

    // Linear interpolation
    return min + (max - min) * factor
}

// Helper function to clamp a value between a min and max
function clamp(value, min2, max2) {
    return min(max(value, min2), max2)
}

