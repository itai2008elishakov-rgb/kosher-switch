package dev.kosherswitch

import android.graphics.Path

/** Reads SVG path data (M L H V C S Q T Z, absolute and relative) into an Android Path. */
object SvgPath {
    fun parse(d: String): Path {
        val path = Path()
        val tokens = Regex("[MmLlHhVvCcSsQqTtZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        var x = 0f; var y = 0f          // current point
        var sx = 0f; var sy = 0f        // subpath start
        var cx = 0f; var cy = 0f        // last control point, for S/T
        var lastCmd = ' '
        fun num() = tokens[i++].toFloat()
        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) cmd = tokens[i++][0]
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            when (cmd.uppercaseChar()) {
                'M' -> {
                    x = ox + num(); y = oy + num(); sx = x; sy = y
                    path.moveTo(x, y)
                    cmd = if (rel) 'l' else 'L' // further pairs are line-tos
                }
                'L' -> { x = ox + num(); y = oy + num(); path.lineTo(x, y) }
                'H' -> { x = ox + num(); path.lineTo(x, y) }
                'V' -> { y = oy + num(); path.lineTo(x, y) }
                'C' -> {
                    val x1 = ox + num(); val y1 = oy + num()
                    cx = ox + num(); cy = oy + num()
                    x = ox + num(); y = oy + num()
                    path.cubicTo(x1, y1, cx, cy, x, y)
                }
                'S' -> {
                    val (x1, y1) = if (lastCmd.uppercaseChar() in "CS") 2 * x - cx to 2 * y - cy else x to y
                    cx = ox + num(); cy = oy + num()
                    x = ox + num(); y = oy + num()
                    path.cubicTo(x1, y1, cx, cy, x, y)
                }
                'Q' -> {
                    cx = ox + num(); cy = oy + num()
                    x = ox + num(); y = oy + num()
                    path.quadTo(cx, cy, x, y)
                }
                'T' -> {
                    if (lastCmd.uppercaseChar() in "QT") { cx = 2 * x - cx; cy = 2 * y - cy } else { cx = x; cy = y }
                    x = ox + num(); y = oy + num()
                    path.quadTo(cx, cy, x, y)
                }
                'Z' -> { path.close(); x = sx; y = sy }
            }
            lastCmd = cmd
        }
        return path
    }
}
