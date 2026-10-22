package cl.reabill.monitor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

data class LabelStyle(
    val align: Int = 0,
    val vpos: Int = 1,
    val compact: Boolean = true,
    val scale: Int = 100,
    val rotate180: Boolean = false,
    val twoLines: Boolean = false
)

object LabelRenderer {
    fun lines(d: LabelData, f: Set<String>, names: Map<String, String> = emptyMap(), twoLines: Boolean = false): List<String> =
        LabelText.lines(d, f, names, twoLines).map { it.text }

    fun render(
        d: LabelData, lengthMm: Int, fields: Set<String>, names: Map<String, String>, style: LabelStyle
    ): Bitmap {
        val w = LabelProtocol.mmToDots(lengthMm)
        val h = LabelProtocol.HEAD_DOTS
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val ls = LabelText.lines(d, fields, names, style.twoLines)
        if (ls.isNotEmpty()) {
            val avail = w - 28f
            fun encoger(p: Paint, texto: String) {
                val tw = p.measureText(texto)
                if (tw > avail) p.textSize = p.textSize * avail / tw
            }
            val paints = ls.map { l ->
                val base = when (ls.size) {
                    1 -> 48f
                    2 -> 36f
                    3 -> if (l.bold) 34f else 26f
                    else -> if (l.bold) 30f else 22f
                }
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    typeface = if (l.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    textSize = base
                    encoger(this, l.text)
                }
            }
            val alturaBase = paints.sumOf { (it.fontMetrics.descent - it.fontMetrics.ascent).toDouble() }.toFloat()
            val k = LabelFit.factor(alturaBase, ls.size, style.scale)
            paints.forEachIndexed { i, p -> p.textSize = p.textSize * k; encoger(p, ls[i].text) }
            val heights = paints.map { it.fontMetrics.descent - it.fontMetrics.ascent }
            val total = heights.sum()
            val gap = if (style.compact) LabelFit.HUECO else maxOf(2f, (h - total) / (ls.size + 1))
            val bloque = total + gap * (ls.size - 1)
            var y = if (style.compact) when (style.vpos) {
                0 -> 2f
                2 -> h - bloque - 2f
                else -> (h - bloque) / 2f
            } else gap
            ls.forEachIndexed { i, l ->
                val p = paints[i]
                val tw = p.measureText(l.text)
                val x = when (style.align) { 1 -> (w - tw) / 2f; 2 -> w - 12f - tw; else -> 12f }
                c.drawText(l.text, x, y - p.fontMetrics.ascent, p)
                y += heights[i] + gap
            }
        }
        return if (style.rotate180) {
            val m = android.graphics.Matrix().apply { postRotate(180f) }
            Bitmap.createBitmap(bmp, 0, 0, w, h, m, false)
        } else bmp
    }

    fun toBlack(bmp: Bitmap): Array<BooleanArray> {
        val w = bmp.width
        val px = IntArray(w * bmp.height)
        bmp.getPixels(px, 0, w, 0, 0, w, bmp.height)
        return Array(bmp.height) { y ->
            BooleanArray(w) { x ->
                val v = px[y * w + x]
                (Color.red(v) * 299 + Color.green(v) * 587 + Color.blue(v) * 114) / 1000 < 128
            }
        }
    }
}
