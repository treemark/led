package com.xmas.analysis;

import org.opencv.core.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;

/**
 * Generates an HTML visualization of LED positions with blinking animation.
 */
public class HtmlVisualizationGenerator {

    private static final Logger logger = LoggerFactory.getLogger(HtmlVisualizationGenerator.class);

    /**
     * Generates an HTML file visualizing LED positions with blinking animation.
     *
     * @param normalizedPositions LED positions normalized to 0-1 range
     * @param outputPath          path to write the HTML file
     * @param videoFileName       original video filename for display
     */
    public void generate(Map<Integer, double[]> normalizedPositions, Path outputPath, String videoFileName) 
            throws IOException {
        
        int ledCount = normalizedPositions.size();
        logger.info("Generating HTML visualization for {} LEDs", ledCount);

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath.toFile()))) {
            writer.println(generateHtml(normalizedPositions, videoFileName));
        }

        logger.info("HTML visualization written to: {}", outputPath);
    }

    private String generateHtml(Map<Integer, double[]> positions, String videoFileName) {
        StringBuilder positionsJson = new StringBuilder();
        positionsJson.append("[\n");
        
        int maxIndex = positions.keySet().stream().mapToInt(i -> i).max().orElse(0);
        
        for (int i = 0; i <= maxIndex; i++) {
            double[] pos = positions.get(i);
            if (pos != null) {
                positionsJson.append(String.format("    [%.6f, %.6f]", pos[0], pos[1]));
            } else {
                positionsJson.append("    null");
            }
            if (i < maxIndex) {
                positionsJson.append(",");
            }
            positionsJson.append("\n");
        }
        positionsJson.append("  ]");

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LED Visualization - %s</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            background: #0a0a0f;
            min-height: 100vh;
            font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
            color: #e0e0e0;
            overflow-x: hidden;
        }
        
        .container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 20px;
        }
        
        header {
            text-align: center;
            padding: 30px 0;
            border-bottom: 1px solid #2a2a3a;
            margin-bottom: 30px;
        }
        
        h1 {
            font-size: 2.5rem;
            font-weight: 300;
            background: linear-gradient(135deg, #00ff88, #00aaff, #ff00aa);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            margin-bottom: 10px;
        }
        
        .subtitle {
            color: #888;
            font-size: 0.9rem;
        }
        
        .stats {
            display: flex;
            justify-content: center;
            gap: 40px;
            margin: 20px 0;
            flex-wrap: wrap;
        }
        
        .stat {
            text-align: center;
        }
        
        .stat-value {
            font-size: 2rem;
            font-weight: bold;
            color: #00ff88;
        }
        
        .stat-label {
            font-size: 0.8rem;
            color: #666;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        
        .visualization-container {
            position: relative;
            width: 100%%;
            max-width: 1000px;
            margin: 0 auto;
            aspect-ratio: 1;
            background: linear-gradient(180deg, #0f0f18 0%%, #151520 100%%);
            border-radius: 12px;
            border: 1px solid #2a2a3a;
            overflow: hidden;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
        }
        
        #ledCanvas {
            width: 100%%;
            height: 100%%;
        }
        
        .controls {
            display: flex;
            justify-content: center;
            gap: 15px;
            margin: 30px 0;
            flex-wrap: wrap;
        }
        
        button {
            background: linear-gradient(135deg, #1a1a2e, #252540);
            border: 1px solid #3a3a5a;
            color: #fff;
            padding: 12px 24px;
            border-radius: 8px;
            cursor: pointer;
            font-family: inherit;
            font-size: 0.9rem;
            transition: all 0.2s;
        }
        
        button:hover {
            background: linear-gradient(135deg, #252540, #3a3a5a);
            border-color: #00ff88;
            box-shadow: 0 0 20px rgba(0, 255, 136, 0.2);
        }
        
        button.active {
            background: linear-gradient(135deg, #00ff88, #00cc6a);
            color: #000;
            border-color: #00ff88;
        }
        
        .speed-control {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        
        .speed-control label {
            color: #888;
        }
        
        input[type="range"] {
            -webkit-appearance: none;
            width: 150px;
            height: 6px;
            background: #2a2a3a;
            border-radius: 3px;
            outline: none;
        }
        
        input[type="range"]::-webkit-slider-thumb {
            -webkit-appearance: none;
            width: 18px;
            height: 18px;
            background: #00ff88;
            border-radius: 50%%;
            cursor: pointer;
        }
        
        .current-led {
            text-align: center;
            margin: 20px 0;
            font-size: 1.2rem;
        }
        
        .current-led span {
            color: #00ff88;
            font-weight: bold;
        }
        
        .legend {
            display: flex;
            justify-content: center;
            gap: 30px;
            margin: 20px 0;
        }
        
        .legend-item {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        
        .legend-dot {
            width: 12px;
            height: 12px;
            border-radius: 50%%;
        }
        
        .legend-dot.blue { background: #0088ff; box-shadow: 0 0 10px #0088ff; }
        .legend-dot.green { background: #00ff88; box-shadow: 0 0 10px #00ff88; }
        .legend-dot.red { background: #ff4466; box-shadow: 0 0 10px #ff4466; }
        .legend-dot.dim { background: #333; }
        
        footer {
            text-align: center;
            padding: 30px;
            color: #444;
            font-size: 0.8rem;
            border-top: 1px solid #2a2a3a;
            margin-top: 40px;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>✨ LED Position Visualization</h1>
            <p class="subtitle">Source: %s</p>
        </header>
        
        <div class="stats">
            <div class="stat">
                <div class="stat-value" id="ledCount">%d</div>
                <div class="stat-label">Total LEDs</div>
            </div>
            <div class="stat">
                <div class="stat-value" id="currentIndex">0</div>
                <div class="stat-label">Current Index</div>
            </div>
            <div class="stat">
                <div class="stat-value" id="speedDisplay">100</div>
                <div class="stat-label">Speed (ms)</div>
            </div>
        </div>
        
        <div class="legend">
            <div class="legend-item">
                <div class="legend-dot blue"></div>
                <span>First (Blue)</span>
            </div>
            <div class="legend-item">
                <div class="legend-dot green"></div>
                <span>Current (Green)</span>
            </div>
            <div class="legend-item">
                <div class="legend-dot red"></div>
                <span>Last (Red)</span>
            </div>
            <div class="legend-item">
                <div class="legend-dot dim"></div>
                <span>Inactive</span>
            </div>
        </div>
        
        <div class="visualization-container">
            <canvas id="ledCanvas"></canvas>
        </div>
        
        <div class="controls">
            <button id="playBtn" class="active">▶ Play</button>
            <button id="pauseBtn">⏸ Pause</button>
            <button id="resetBtn">↺ Reset</button>
            <button id="showAllBtn">◉ Show All</button>
            <div class="speed-control">
                <label>Speed:</label>
                <input type="range" id="speedSlider" min="10" max="500" value="100">
            </div>
        </div>
        
        <footer>
            Generated by LED Sequence Analyzer • OpenCV + Spring Boot
        </footer>
    </div>
    
    <script>
        const ledPositions = %s;
        
        const canvas = document.getElementById('ledCanvas');
        const ctx = canvas.getContext('2d');
        
        let currentIndex = 0;
        let isPlaying = true;
        let showAll = false;
        let speed = 100;
        let animationTimer = null;
        
        // High DPI support
        function setupCanvas() {
            const rect = canvas.getBoundingClientRect();
            const dpr = window.devicePixelRatio || 1;
            canvas.width = rect.width * dpr;
            canvas.height = rect.height * dpr;
            ctx.scale(dpr, dpr);
            canvas.style.width = rect.width + 'px';
            canvas.style.height = rect.height + 'px';
        }
        
        function drawLed(x, y, color, size, glow) {
            const rect = canvas.getBoundingClientRect();
            const px = x * rect.width;
            const py = (1 - y) * rect.height; // Flip Y for screen coordinates
            
            // Glow effect
            if (glow) {
                const gradient = ctx.createRadialGradient(px, py, 0, px, py, size * 3);
                gradient.addColorStop(0, color);
                gradient.addColorStop(0.5, color + '40');
                gradient.addColorStop(1, 'transparent');
                ctx.fillStyle = gradient;
                ctx.beginPath();
                ctx.arc(px, py, size * 3, 0, Math.PI * 2);
                ctx.fill();
            }
            
            // Core
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.arc(px, py, size, 0, Math.PI * 2);
            ctx.fill();
        }
        
        function draw() {
            const rect = canvas.getBoundingClientRect();
            ctx.clearRect(0, 0, rect.width, rect.height);
            
            const ledCount = ledPositions.length;
            const baseSize = Math.max(3, Math.min(8, 400 / Math.sqrt(ledCount)));
            
            // Draw all LEDs
            for (let i = 0; i < ledCount; i++) {
                const pos = ledPositions[i];
                if (!pos) continue;
                
                let color, size, glow;
                
                if (i === 0) {
                    // First LED - Blue
                    color = '#0088ff';
                    size = baseSize * 1.2;
                    glow = true;
                } else if (i === ledCount - 1) {
                    // Last LED - Red
                    color = '#ff4466';
                    size = baseSize * 1.2;
                    glow = true;
                } else if (showAll || i === currentIndex) {
                    // Current or show all - Green
                    color = '#00ff88';
                    size = baseSize * 1.3;
                    glow = true;
                } else if (i < currentIndex) {
                    // Already visited - dimmer green
                    color = '#00884444';
                    size = baseSize * 0.8;
                    glow = false;
                } else {
                    // Not yet visited - dim
                    color = '#333333';
                    size = baseSize * 0.6;
                    glow = false;
                }
                
                drawLed(pos[0], pos[1], color, size, glow);
            }
            
            // Draw index number on current LED
            if (!showAll && ledPositions[currentIndex]) {
                const pos = ledPositions[currentIndex];
                const px = pos[0] * rect.width;
                const py = (1 - pos[1]) * rect.height;
                ctx.fillStyle = '#fff';
                ctx.font = 'bold 12px monospace';
                ctx.textAlign = 'center';
                ctx.fillText(currentIndex.toString(), px, py - baseSize * 2);
            }
        }
        
        function nextFrame() {
            if (!isPlaying) return;
            
            currentIndex = (currentIndex + 1) %% ledPositions.length;
            document.getElementById('currentIndex').textContent = currentIndex;
            draw();
            
            animationTimer = setTimeout(nextFrame, speed);
        }
        
        function startAnimation() {
            isPlaying = true;
            showAll = false;
            document.getElementById('playBtn').classList.add('active');
            document.getElementById('pauseBtn').classList.remove('active');
            document.getElementById('showAllBtn').classList.remove('active');
            if (animationTimer) clearTimeout(animationTimer);
            nextFrame();
        }
        
        function pauseAnimation() {
            isPlaying = false;
            document.getElementById('playBtn').classList.remove('active');
            document.getElementById('pauseBtn').classList.add('active');
            if (animationTimer) clearTimeout(animationTimer);
        }
        
        function resetAnimation() {
            currentIndex = 0;
            document.getElementById('currentIndex').textContent = 0;
            draw();
        }
        
        function toggleShowAll() {
            showAll = !showAll;
            isPlaying = false;
            document.getElementById('showAllBtn').classList.toggle('active', showAll);
            document.getElementById('playBtn').classList.remove('active');
            document.getElementById('pauseBtn').classList.remove('active');
            if (animationTimer) clearTimeout(animationTimer);
            draw();
        }
        
        // Event listeners
        document.getElementById('playBtn').addEventListener('click', startAnimation);
        document.getElementById('pauseBtn').addEventListener('click', pauseAnimation);
        document.getElementById('resetBtn').addEventListener('click', resetAnimation);
        document.getElementById('showAllBtn').addEventListener('click', toggleShowAll);
        
        document.getElementById('speedSlider').addEventListener('input', (e) => {
            speed = parseInt(e.target.value);
            document.getElementById('speedDisplay').textContent = speed;
        });
        
        window.addEventListener('resize', () => {
            setupCanvas();
            draw();
        });
        
        // Initialize
        setupCanvas();
        draw();
        startAnimation();
    </script>
</body>
</html>
""".formatted(videoFileName, videoFileName, positions.size(), positionsJson.toString());
    }
}

