export function beforeRender(delta) {
    t1 = time(.1)
}

export function render(index) {
    h = t1 + index/pixelCount
    s = 1
    v = 1
    hsv(h, s, v)
}


/*
  Advanced Marquee Pattern v6

  This pattern animates ASCII characters scrolling across an LED matrix.

  Features:
  - Sizes: Small, Med, Large, Full (Zoom control)
  - Colors: Solid, Rainbow, Gradient
  - Gradient: 3 Full Color Pickers

  Original Author: Jeff Vyduna (https://ngnr.org)
  Bugfixes: Zeb (https://forum.electromage.com/u/zeb)
  Updates (v3-v6): Added specific controls for Zoom, Gradients, and fixed rendering for Pixelblaze v3+.

  Performance Note:
  This version uses a "redraw-every-frame" approach in render2D instead of a circular buffer.
  On Pixelblaze v3, this is fast enough for standard matrices (e.g. 8x8 to 32x32) and prevents
  buffer synchronization bugs when switching directions or sizes.
*/

/* CONFIGURATION */
// Exported for setting via webSockets
// Javascript integers correspond to ASCII codes.
// Blade Runner Quotes: "A new life awaits..."
export var message = [
    84, 97, 109, 97, 121, 111, // Tamayo

]

/*
  ASCII Chart

  32      48 0    65 A   74 J    83 S    97  a    106 j    115 s
  33 !    49 1    66 B   75 K    84 T    98  b    107 k    116 t
  34 "    50 2    67 C   76 L    85 U    99  c    108 l    117 u
  35 #    51 3    68 D   77 M    86 V    100 d    109 m    118 v
  36 $    52 4    69 E   78 N    87 W    101 e    110 n    119 w
  37 %    53 5    70 F   79 O    88 X    102 f    111 o    120 x
  38 &    54 6    71 G   80 P    89 Y    103 g    112 p    121 y
  39 '    55 7    72 H   81 Q    90 Z    104 h    113 q    122 z
  40 (    56 8    73 I   82 R            105 i    114 r
  41 )    57 9
  42 *    58 :                   91 [                      123 {
  43 +    59 ;                   92 \                      124 |
  44 ,    60 <                   93 ]                      125 }
  45 -    61 =                   94 ^                      126 ~
  46 .    62 >                   95 _
  47 /    63 ?                   96 `
          64 @
*/

/* ===========================
   UI CONTROLS
   =========================== */

// --- SPEED & DIRECTION ---
export var speedVar = 0.5 // Default reasonable speed
export var direction = -1 // -1 = Left (Normal for this matrix), 1 = Right
export function sliderSpeed(v) {
    // Linear speed control (Range 0 to 5)
    speedVar = v * 5
}
export function triggerLeftToRight() { direction = -1 }
export function triggerRightToLeft() { direction = 1 }

// --- SIZES ---
export var patternScale = 1
export function triggerSizeSmall() { patternScale = 1 }
export function triggerSizeMedium() { patternScale = 2 }
export function triggerSizeLarge() { patternScale = 3 }
export function triggerSizeFull() { patternScale = 27 / 8 }

// --- ORIENTATION ---
export var vertical = 0
export function triggerOrientationHorizontal() { vertical = 0 }
export function triggerOrientationVertical() { vertical = 1 }

// --- MIRRORING REMOVED (Hardcoded below)

// --- COLORS ---
export var colorMode = 0 // 0=Solid, 1=Rainbow, 2=Gradient
export function triggerModeSolid() { colorMode = 0 }
export function triggerModeRainbow() { colorMode = 1 }
export function triggerModeGradient() { colorMode = 2 }

// Solid Color Picker
export var mainHue = 0
export var mainSat = 1
export function hsvPickerColor(h, s, v) {
    mainHue = h
    mainSat = s
}

// Gradient Pickers (Top, Mid, Bottom)
export var gTopH = 0, gTopS = 1, gTopV = 1
export var gMidH = 0.33, gMidS = 1, gMidV = 1
export var gBotH = 0.66, gBotS = 1, gBotV = 1

export function hsvPickerGradTop(h, s, v) { gTopH = h; gTopS = s; gTopV = v }
export function hsvPickerGradMid(h, s, v) { gMidH = h; gMidS = s; gMidV = v }
export function hsvPickerGradBot(h, s, v) { gBotH = h; gBotS = s; gBotV = v }


/* ===========================
   LOGIC & RENDERING
   =========================== */

/*
  Font Implementation

  Pixelblaze currently supports up to 64 arrays with 2048 array elements.
  To store a character set of 8x8 bit characters, we use 8 arrays, one for each row.

  Four 8-bit maps are packed into each 32 bit array element. This makes the
  bitwise code a little hard to follow, but uses memory efficiently.

  See original script for packing diagram.
*/
var charRows = 8
var fontCharCount = 128
var fontBitmap = array(charRows)
for (row = 0; row < charRows; row++) fontBitmap[row] = array(fontCharCount / 4)

var character = array(charRows)
var matrixRows = 27
var matrixCols = 21

var renderBuffer = array(matrixRows)
for (row = 0; row < matrixRows; row++) renderBuffer[row] = array(matrixCols)

var scrollPos = 0

export function beforeRender(delta) {
    // Standard scroll calculation
    scrollPos += (delta / 50) * speedVar * direction

    // Clear
    for (r = 0; r < matrixRows; r++) {
        for (c = 0; c < matrixCols; c++) renderBuffer[r][c] = 0
    }

    // Draw to buffer
    var shift = scrollPos

    if (vertical) {
        // VERTICAL STACK MODE
        // Loop total height = message * 8
        var totalH = message.length * 8
        var loopH = totalH + matrixRows // Space between loops

        // Center horizontally
        // 8 pixels wide. matrixCols is width.
        var startCol = floor((matrixCols - 8) / 2)

        for (var i = 0; i < message.length; i++) {
            // Calculate Y position for this character
            // Letters stack: 0, 8, 16...
            var charBaseY = (i * 8) - shift

            // Wrap loop
            var offset = charBaseY % loopH
            if (offset < -totalH) offset += loopH

            // Draw if visible
            // Note: offset is the Top row of the char
            if (offset > -8 && offset < matrixRows) {
                // drawChar(ascii, row, col)
                drawChar(message[i], floor(offset), startCol)
            }
        }
    } else {
        // HORIZONTAL SCROLL MODE (Standard)
        var totalW = message.length * 8
        var loopW = totalW + matrixCols

        var startRow = floor((matrixRows - 8) / 2)

        for (var i = 0; i < message.length; i++) {
            var charBaseX = (i * 8) - shift
            var offset = charBaseX % loopW
            if (offset < -totalW) offset += loopW

            if (offset > -8 && offset < matrixCols) {
                drawChar(message[i], startRow, floor(offset))
            }
        }
    }
}

function drawChar(ascii, rOffset, cOffset) {
    fetchCharacter(ascii)
    for (var row = 0; row < 8; row++) {
        var bits = character[row]
        for (var col = 0; col < 8; col++) {
            if ((bits >> (7 - col)) & 1) {
                var targetR = rOffset + row
                var targetC = cOffset + col
                if (targetR >= 0 && targetR < matrixRows &&
                    targetC >= 0 && targetC < matrixCols) {
                    renderBuffer[targetR][targetC] = 1
                }
            }
        }
    }
}

export function render2D(index, x, y) {
    // Always map native visual coordinates
    // We handle "Vertical Mode" by drawing differently to the buffer
    renderMain(index, x, y)
}

function renderMain(index, x, y) {
    // Zoom Logic
    var cx = (x - 0.5) / patternScale + 0.5
    var cy = (y - 0.5) / patternScale + 0.5

    // Hardcoded Fix: Mirror X Only
    // Invert X to fix "Backwards". Y is left Native to fix "Upside Down".
    cx = 1 - cx
    // cy = 1 - cy  <-- REMOVED (Caused Upside Down)

    // Flip Y (Removed because we are fixing the Matrix Map to be Top-Left 0,0)
    // var r = floor(cy * (matrixRows - 0.001))
    // r = (matrixRows - 1) - r  <-- REMOVED

    // Standard mapping:
    var r = floor(cy * (matrixRows - 0.001))

    var c = floor(cx * (matrixCols - 0.001))

    if (r >= 0 && r < matrixRows && c >= 0 && c < matrixCols) {
        if (renderBuffer[r][c] > 0) {
            if (colorMode == 0) {
                hsv(mainHue, mainSat, 1)
            }
            else if (colorMode == 1) {
                // Rainbow
                hsv(x + time(0.1), 1, 1)
            }
            else {
                // Gradient (Top -> Mid -> Bot)
                // y is 0..1 (before flip? Engine provides 0..1).
                // render2D(index, x, y) provides coordinates.
                // We want gradient to follow SCREEN Y, not buffer Y.
                // So we use 'y' directly.
                var h, s, v
                if (y < 0.5) {
                    // Top to Mid
                    var t = y * 2
                    h = mix(gTopH, gMidH, t)
                    s = mix(gTopS, gMidS, t)
                    v = mix(gTopV, gMidV, t)
                } else {
                    // Mid to Bot
                    var t = (y - 0.5) * 2
                    h = mix(gMidH, gBotH, t)
                    s = mix(gMidS, gBotS, t)
                    v = mix(gMidV, gBotV, t)
                }
                hsv(h, s, v)
            }
        } else {
            hsv(0, 0, 0)
        }
    } else {
        hsv(0, 0, 0)
    }
}

// --- FONT ENGINE ---
function fetchCharacter(charIndex) {
    var element = floor(charIndex / 4)
    var bank = charIndex % 4
    for (var row = 0; row < charRows; row++) {
        character[row] = unpackByte(row, element, bank)
    }
}
function unpackByte(row, element, bank) {
    var word = fontBitmap[row][element]
    var b = 0
    if (bank > 1) b = word << (8 * (bank - 1))
    else if (bank == 0) b = word >> 8
    else b = word
    return b & 0xFF
}
function storeCharacter(charIndex, r0, r1, r2, r3, r4, r5, r6, r7) {
    var element = floor(charIndex / 4)
    var bank = charIndex % 4
    packByte(0, element, bank, r0)
    packByte(1, element, bank, r1)
    packByte(2, element, bank, r2)
    packByte(3, element, bank, r3)
    packByte(4, element, bank, r4)
    packByte(5, element, bank, r5)
    packByte(6, element, bank, r6)
    packByte(7, element, bank, r7)
}
var byteHolder = array(4)
function packByte(row, element, bank, byte) {
    var original = fontBitmap[row][element]
    for (var i = 0; i < 4; i++) {
        byteHolder[i] = (((original << (i * 8)) & 0xFF00) >> 8) & 0xFF
    }
    byteHolder[bank] = byte
    fontBitmap[row][element] = (byteHolder[0] << 8) + byteHolder[1] + (byteHolder[2] >> 8) + (byteHolder[3] >> 16)
}

/* FONT DATA */
storeCharacter(32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
storeCharacter(33, 0x30, 0x78, 0x78, 0x30, 0x30, 0x00, 0x30, 0x00)
storeCharacter(34, 0x6c, 0x6c, 0x6c, 0x00, 0x00, 0x00, 0x00, 0x00)
storeCharacter(35, 0x6c, 0x6c, 0xfe, 0x6c, 0xfe, 0x6c, 0x6c, 0x00)
storeCharacter(36, 0x30, 0x7c, 0xc0, 0x78, 0x0c, 0xf8, 0x30, 0x00)
storeCharacter(37, 0x00, 0xc6, 0xcc, 0x18, 0x30, 0x66, 0xc6, 0x00)
storeCharacter(38, 0x38, 0x6c, 0x38, 0x76, 0xdc, 0xcc, 0x76, 0x00)
storeCharacter(39, 0x60, 0x60, 0xc0, 0x00, 0x00, 0x00, 0x00, 0x00)
storeCharacter(40, 0x18, 0x30, 0x60, 0x60, 0x60, 0x30, 0x18, 0x00)
storeCharacter(41, 0x60, 0x30, 0x18, 0x18, 0x18, 0x30, 0x60, 0x00)
storeCharacter(42, 0x00, 0x66, 0x3c, 0xff, 0x3c, 0x66, 0x00, 0x00)
storeCharacter(43, 0x00, 0x30, 0x30, 0xfc, 0x30, 0x30, 0x00, 0x00)
storeCharacter(44, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x30, 0x60)
storeCharacter(45, 0x00, 0x00, 0x00, 0xfc, 0x00, 0x00, 0x00, 0x00)
storeCharacter(46, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x30, 0x00)
storeCharacter(47, 0x06, 0x0c, 0x18, 0x30, 0x60, 0xc0, 0x80, 0x00)
storeCharacter(48, 0x7c, 0xc6, 0xce, 0xde, 0xf6, 0xe6, 0x7c, 0x00)
storeCharacter(49, 0x30, 0x70, 0x30, 0x30, 0x30, 0x30, 0xfc, 0x00)
storeCharacter(50, 0x78, 0xcc, 0x0c, 0x38, 0x60, 0xc4, 0xfc, 0x00)
storeCharacter(51, 0x78, 0xcc, 0x0c, 0x38, 0x0c, 0xcc, 0x78, 0x00)
storeCharacter(52, 0x1c, 0x3c, 0x6c, 0xcc, 0xfe, 0x0c, 0x1e, 0x00)
storeCharacter(53, 0xfc, 0xc0, 0xf8, 0x0c, 0x0c, 0xcc, 0x78, 0x00)
storeCharacter(54, 0x38, 0x60, 0xc0, 0xf8, 0xcc, 0xcc, 0x78, 0x00)
storeCharacter(55, 0xfc, 0xcc, 0x0c, 0x18, 0x30, 0x30, 0x30, 0x00)
storeCharacter(56, 0x78, 0xcc, 0xcc, 0x78, 0xcc, 0xcc, 0x78, 0x00)
storeCharacter(57, 0x78, 0xcc, 0xcc, 0x7c, 0x0c, 0x18, 0x70, 0x00)
storeCharacter(58, 0x00, 0x30, 0x30, 0x00, 0x00, 0x30, 0x30, 0x00)
storeCharacter(59, 0x00, 0x30, 0x30, 0x00, 0x30, 0x30, 0x60, 0x00)
storeCharacter(60, 0x18, 0x30, 0x60, 0xc0, 0x60, 0x30, 0x18, 0x00)
storeCharacter(61, 0x00, 0x00, 0xfc, 0x00, 0x00, 0xfc, 0x00, 0x00)
storeCharacter(62, 0x60, 0x30, 0x18, 0x0c, 0x18, 0x30, 0x60, 0x00)
storeCharacter(63, 0x78, 0xcc, 0x0c, 0x18, 0x30, 0x00, 0x30, 0x00)
storeCharacter(64, 0x7c, 0xc6, 0xde, 0xde, 0xde, 0xc0, 0x78, 0x00)
storeCharacter(65, 0x30, 0x78, 0xcc, 0xcc, 0xfc, 0xcc, 0xcc, 0x00)
storeCharacter(66, 0xfc, 0x66, 0x66, 0x7c, 0x66, 0x66, 0xfc, 0x00)
storeCharacter(67, 0x3c, 0x66, 0xc0, 0xc0, 0xc0, 0x66, 0x3c, 0x00)
storeCharacter(68, 0xf8, 0x6c, 0x66, 0x66, 0x66, 0x6c, 0xf8, 0x00)
storeCharacter(69, 0xfe, 0x62, 0x68, 0x78, 0x68, 0x62, 0xfe, 0x00)
storeCharacter(70, 0xfe, 0x62, 0x68, 0x78, 0x68, 0x60, 0xf0, 0x00)
storeCharacter(71, 0x3c, 0x66, 0xc0, 0xc0, 0xce, 0x66, 0x3e, 0x00)
storeCharacter(72, 0xcc, 0xcc, 0xcc, 0xfc, 0xcc, 0xcc, 0xcc, 0x00)
storeCharacter(73, 0x78, 0x30, 0x30, 0x30, 0x30, 0x30, 0x78, 0x00)
storeCharacter(74, 0x1e, 0x0c, 0x0c, 0x0c, 0xcc, 0xcc, 0x78, 0x00)
storeCharacter(75, 0xe6, 0x66, 0x6c, 0x78, 0x6c, 0x66, 0xe6, 0x00)
storeCharacter(76, 0xf0, 0x60, 0x60, 0x60, 0x62, 0x66, 0xfe, 0x00)
storeCharacter(77, 0xc6, 0xee, 0xfe, 0xfe, 0xd6, 0xc6, 0xc6, 0x00)
storeCharacter(78, 0xc6, 0xe6, 0xf6, 0xde, 0xce, 0xc6, 0xc6, 0x00)
storeCharacter(79, 0x38, 0x6c, 0xc6, 0xc6, 0xc6, 0x6c, 0x38, 0x00)
storeCharacter(80, 0xfc, 0x66, 0x66, 0x7c, 0x60, 0x60, 0xf0, 0x00)
storeCharacter(81, 0x78, 0xcc, 0xcc, 0xcc, 0xdc, 0x78, 0x1c, 0x00)
storeCharacter(82, 0xfc, 0x66, 0x66, 0x7c, 0x6c, 0x66, 0xe6, 0x00)
storeCharacter(83, 0x78, 0xcc, 0xe0, 0x70, 0x1c, 0xcc, 0x78, 0x00)
storeCharacter(84, 0xfc, 0xb4, 0x30, 0x30, 0x30, 0x30, 0x78, 0x00)
storeCharacter(85, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xfc, 0x00)
storeCharacter(86, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0x78, 0x30, 0x00)
storeCharacter(87, 0xc6, 0xc6, 0xc6, 0xd6, 0xfe, 0xee, 0xc6, 0x00)
storeCharacter(88, 0xc6, 0xc6, 0x6c, 0x38, 0x38, 0x6c, 0xc6, 0x00)
storeCharacter(89, 0xcc, 0xcc, 0xcc, 0x78, 0x30, 0x30, 0x78, 0x00)
storeCharacter(90, 0xfe, 0xc6, 0x8c, 0x18, 0x32, 0x66, 0xfe, 0x00)
storeCharacter(91, 0x78, 0x60, 0x60, 0x60, 0x60, 0x60, 0x78, 0x00)
storeCharacter(92, 0xc0, 0x60, 0x30, 0x18, 0x0c, 0x06, 0x02, 0x00)
storeCharacter(93, 0x78, 0x18, 0x18, 0x18, 0x18, 0x18, 0x78, 0x00)
storeCharacter(94, 0x10, 0x38, 0x6c, 0xc6, 0x00, 0x00, 0x00, 0x00)
storeCharacter(95, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff)
storeCharacter(96, 0x30, 0x30, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00)
storeCharacter(97, 0x00, 0x00, 0x78, 0x0c, 0x7c, 0xcc, 0x76, 0x00)
storeCharacter(98, 0xe0, 0x60, 0x60, 0x7c, 0x66, 0x66, 0xdc, 0x00)
storeCharacter(99, 0x00, 0x00, 0x78, 0xcc, 0xc0, 0xcc, 0x78, 0x00)
storeCharacter(100, 0x1c, 0x0c, 0x0c, 0x7c, 0xcc, 0xcc, 0x76, 0x00)
storeCharacter(101, 0x00, 0x00, 0x78, 0xcc, 0xfc, 0xc0, 0x78, 0x00)
storeCharacter(102, 0x38, 0x6c, 0x60, 0xf0, 0x60, 0x60, 0xf0, 0x00)
storeCharacter(103, 0x00, 0x00, 0x76, 0xcc, 0xcc, 0x7c, 0x0c, 0xf8)
storeCharacter(104, 0xe0, 0x60, 0x6c, 0x76, 0x66, 0x66, 0xe6, 0x00)
storeCharacter(105, 0x30, 0x00, 0x70, 0x30, 0x30, 0x30, 0x78, 0x00)
storeCharacter(106, 0x0c, 0x00, 0x0c, 0x0c, 0x0c, 0xcc, 0xcc, 0x78)
storeCharacter(107, 0xe0, 0x60, 0x66, 0x6c, 0x78, 0x6c, 0xe6, 0x00)
storeCharacter(108, 0x70, 0x30, 0x30, 0x30, 0x30, 0x30, 0x78, 0x00)
storeCharacter(109, 0x00, 0x00, 0xcc, 0xfe, 0xfe, 0xd6, 0xc6, 0x00)
storeCharacter(110, 0x00, 0x00, 0xf8, 0xcc, 0xcc, 0xcc, 0xcc, 0x00)
storeCharacter(111, 0x00, 0x00, 0x78, 0xcc, 0xcc, 0xcc, 0x78, 0x00)
storeCharacter(112, 0x00, 0x00, 0xdc, 0x66, 0x66, 0x7c, 0x60, 0xf0)
storeCharacter(113, 0x00, 0x00, 0x76, 0xcc, 0xcc, 0x7c, 0x0c, 0x1e)
storeCharacter(114, 0x00, 0x00, 0xdc, 0x76, 0x66, 0x60, 0xf0, 0x00)
storeCharacter(115, 0x00, 0x00, 0x7c, 0xc0, 0x78, 0x0c, 0xf8, 0x00)
storeCharacter(116, 0x10, 0x30, 0x7c, 0x30, 0x30, 0x34, 0x18, 0x00)
storeCharacter(117, 0x00, 0x00, 0xcc, 0xcc, 0xcc, 0xcc, 0x76, 0x00)
storeCharacter(118, 0x00, 0x00, 0xcc, 0xcc, 0xcc, 0x78, 0x30, 0x00)
storeCharacter(119, 0x00, 0x00, 0xc6, 0xd6, 0xfe, 0xfe, 0x6c, 0x00)
storeCharacter(120, 0x00, 0x00, 0xc6, 0x6c, 0x38, 0x6c, 0xc6, 0x00)
storeCharacter(121, 0x00, 0x00, 0xcc, 0xcc, 0xcc, 0x7c, 0x0c, 0xf8)
storeCharacter(122, 0x00, 0x00, 0xfc, 0x98, 0x30, 0x64, 0xfc, 0x00)
storeCharacter(123, 0x1c, 0x30, 0x30, 0xe0, 0x30, 0x30, 0x1c, 0x00)
storeCharacter(124, 0x18, 0x18, 0x18, 0x00, 0x18, 0x18, 0x18, 0x00)
storeCharacter(125, 0xe0, 0x30, 0x30, 0x1c, 0x30, 0x30, 0xe0, 0x00)
storeCharacter(126, 0x76, 0xdc, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
