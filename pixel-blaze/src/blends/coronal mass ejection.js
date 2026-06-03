// Coronal Mass Ejection 2D
// A demonstration of Pixelblaze's Perlin noise and smoothstep functions
//
// 10/09/2022 ZRanger1
// 5/29/2024 wizard - controls! fix perlin wrapping


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


export function beforeRender(delta) {

    // per-frame animation timers
    t1 = time(.2);
    noiseTime = time(10) * 256;
    noiseYTime = time(8) * 256;

    setPerlinWrap(density,256,256);
    densityPiConversion = 1/PI * density * (mirror ? 1 : .5)

}

export function render2D(index, x, y) {
    // convert to radial coords
    tmp = hypot(x,y); x = atan2(y,x) * densityPiConversion; y = tmp;

    // generate noise field
    v = 1-perlinTurbulence(x,y - noiseYTime,noiseTime,2,gain,iterations)

    // convert noise field to discrete radial "flares"
    v = max(smoothstep(cutoff,1,v),(1-((y*v)-c2)/coreSize));
    v = v * v * v;

    // draw star + stellar flares, always white hot at center
    // occasionally throwing off super hot flare bits
    hsv(t1 - (0.125*v),6.5*y-v,v);
}