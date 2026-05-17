// LED Detection Controller Pattern for Pixelblaze
// Paste this into a new pattern in the Pixelblaze web UI
// 
// This pattern accepts external control via websocket setVars:
// - mode: 0=off, 1=single LED, 2=all LEDs
// - ledIndex: which LED to light (when mode=1)
// - r, g, b: color values (0-1 range)

// Exported variables (controllable via websocket)
export var mode = 0        // 0=off, 1=single, 2=all
export var ledIndex = 0    // Which LED to light (0-255)
export var r = 0           // Red (0-1)
export var g = 0           // Green (0-1)  
export var b = 0           // Blue (0-1)

export function render(index) {
  if (mode == 0) {
    // All off
    rgb(0, 0, 0)
  } else if (mode == 1) {
    // Single LED mode
    if (index == ledIndex) {
      rgb(r, g, b)
    } else {
      rgb(0, 0, 0)
    }
  } else if (mode == 2) {
    // All LEDs same color
    rgb(r, g, b)
  } else {
    rgb(0, 0, 0)
  }
}

