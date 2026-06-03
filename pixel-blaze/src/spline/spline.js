// Pre-initialized global arrays and variables
var splinePoints = [
    [-1, -1],
    [-0.5, 1],
    [0, -1],
    [0.5, 1],
    [1, -1]
];
var splineCoeffs = [
    [0, 0, 0, 0],
    [0, 0, 0, 0],
    [0, 0, 0, 0],
    [0, 0, 0, 0]
];
// var start = 0;
// var end = 0;
// var step = 0;
// var interpolatedX = 0;
// var interpolatedY = 0;
// var closestT = 0;
// var minDist = 0;

function initSpline() {
    var n = splinePoints.length - 1;
    calculateCoefficients();

    // Calculate start, end, and step
    start = 0;
    end = n;
    step = 1 / 10; // Divide the parameter space into 10 segments per spline segment
}

function calculateCoefficients() {
    var n = splinePoints.length - 1;

    for (var i = 0; i < n; i++) {
        var dx = splinePoints[i+1][0] - splinePoints[i][0];
        var dy = splinePoints[i+1][1] - splinePoints[i][1];
        splineCoeffs[i][0] = splinePoints[i][0];
        splineCoeffs[i][1] = splinePoints[i][1];
        splineCoeffs[i][2] = dx;
        splineCoeffs[i][3] = dy;
    }
}

function interpolateSpline(t) {
    var n = splinePoints.length - 1;
    var i = floor(t);

    if (i < 0) {
        interpolatedX = splinePoints[0][0];
        interpolatedY = splinePoints[0][1];
    } else if (i >= n) {
        interpolatedX = splinePoints[n][0];
        interpolatedY = splinePoints[n][1];
    } else {
        var u = t - i;
        var coeff = splineCoeffs[i];
        interpolatedX = coeff[0] + coeff[2] * u;
        interpolatedY = coeff[1] + coeff[3] * u;
    }
}

function distanceToSpline(x0, y0, t) {
    interpolateSpline(t);
    var dx = interpolatedX - x0;
    var dy = interpolatedY - y0;
    return sqrt(dx * dx + dy * dy);
}

function findClosestPoint(x0, y0) {
    minDist = 5;
    closestT = 0;

    for (var t = start; t <= end; t += step) {
        var dist = distanceToSpline(x0, y0, t);
        if (dist < minDist) {
            minDist = dist;
            closestT = t;
        }
    }
}

export function render2D(index, x, y) {
    var nx = (x - 0.5) * 2;
    var ny = (y - 0.5) * 2;

    findClosestPoint(nx, ny);
    if (minDist <= 0.15) {
        hsv(1, 1, 1);
    } else {
        hsv(1, 0, 0);
    }
}

export var t = 0;

export function beforeRender(delta) {
    // You can add any per-frame updates here
    // For example, you might want to animate t:
    // t = (t + delta / 1000) % 1; // Uncomment this line to animate t from 0 to 1
    t=delta;
}

// Initialize the spline
initSpline();

export {start, end, step}
