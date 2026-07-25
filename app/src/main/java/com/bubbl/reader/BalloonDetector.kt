package com.bubbl.reader

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri

/**
 * Detecta o balão sob o ponto tocado por crescimento de região (flood fill):
 * a partir do toque, cresce a área de pixels com brilho parecido até bater no
 * contorno do balão. Devolve o bounding box em coordenadas da imagem original.
 *
 * ponytail: heurística p/ balão de interior uniforme com contorno fechado
 * (mangá P&B típico). Balão colorido/aberto/invertido pode falhar -> null,
 * e o chamador cai no zoom no ponto. Upgrade: modelo ML de detecção de balão.
 */
object BalloonDetector {

    private const val WORK_MAX = 1200        // maior dimensão p/ análise (px)
    private const val TOL = 40               // tolerância de brilho no flood fill
    private const val MAX_FILL_FRAC = 0.35f  // acima disso, vazou = não é balão
    private const val MIN_FILL_RATIO = 0.40f // preenchido/bbox baixo = vazou p/ calha

    /** Bounding box puro (sem android.graphics, p/ ser testável em JVM). */
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** Roda em thread de background. srcW/srcH = dimensões reais da página. */
    fun detect(
        resolver: ContentResolver, uri: Uri,
        srcX: Float, srcY: Float, srcW: Int, srcH: Int
    ): Rect? {
        if (srcW <= 0 || srcH <= 0) return null
        val small = decodeScaled(resolver, uri) ?: return null
        try {
            val w = small.width; val h = small.height
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)

            var sx = (srcX / srcW * w).toInt().coerceIn(0, w - 1)
            var sy = (srcY / srcH * h).toInt().coerceIn(0, h - 1)
            // tocou no texto escuro dentro do balão? re-semeia num pixel claro perto.
            adjustSeed(px, w, h, sx, sy)?.let { sx = it % w; sy = it / w }

            val box = floodFillBounds(px, w, h, sx, sy, TOL, MAX_FILL_FRAC, MIN_FILL_RATIO)
                ?: return null

            // mapeia p/ resolução original + padding
            val fx = srcW.toFloat() / w
            val fy = srcH.toFloat() / h
            val padX = (box.width * fx * 0.04f).toInt()
            val padY = (box.height * fy * 0.04f).toInt()
            return Rect(
                (box.left * fx - padX).toInt().coerceIn(0, srcW - 1),
                (box.top * fy - padY).toInt().coerceIn(0, srcH - 1),
                (box.right * fx + padX).toInt().coerceIn(1, srcW),
                (box.bottom * fy + padY).toInt().coerceIn(1, srcH)
            ).takeIf { it.width() > 8 && it.height() > 8 }
        } finally {
            small.recycle()
        }
    }

    /** Recorte nítido do balão em alta resolução. */
    fun crop(resolver: ContentResolver, uri: Uri, rect: Rect): Bitmap? {
        return resolver.openInputStream(uri)?.use { ins ->
            @Suppress("DEPRECATION")
            val dec = BitmapRegionDecoder.newInstance(ins, false) ?: return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleFor(rect.width(), rect.height(), 1600)
            }
            try { dec.decodeRegion(rect, opts) } finally { dec.recycle() }
        }
    }

    // --- lógica pura, testável ---

    /**
     * Cresce região a partir de (sx,sy) sobre pixels com |brilho - brilho0| <= tol.
     * Devolve bounding box, ou null se a região vazou (> maxFrac da imagem) ou
     * encostou nas 4 bordas (fundo, não balão).
     */
    fun floodFillBounds(
        px: IntArray, w: Int, h: Int, sx: Int, sy: Int,
        tol: Int, maxFrac: Float, minFillRatio: Float = 0f
    ): Bounds? {
        val n = w * h
        if (sx !in 0 until w || sy !in 0 until h) return null
        val base = lum(px[sy * w + sx])
        val seen = BooleanArray(n)
        val stack = IntArray(n)
        var sp = 0
        stack[sp++] = sy * w + sx
        seen[sy * w + sx] = true

        var minX = sx; var maxX = sx; var minY = sy; var maxY = sy
        var count = 0
        val limit = (n * maxFrac).toInt()

        while (sp > 0) {
            val idx = stack[--sp]
            val x = idx % w; val y = idx / w
            if (minX > x) minX = x
            if (maxX < x) maxX = x
            if (minY > y) minY = y
            if (maxY < y) maxY = y
            count++
            if (count > limit) return null   // vazou

            if (x > 0)     sp = tryPush(px, seen, stack, sp, idx - 1, base, tol)
            if (x < w - 1) sp = tryPush(px, seen, stack, sp, idx + 1, base, tol)
            if (y > 0)     sp = tryPush(px, seen, stack, sp, idx - w, base, tol)
            if (y < h - 1) sp = tryPush(px, seen, stack, sp, idx + w, base, tol)
        }

        val touchesAll = minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1
        if (touchesAll) return null
        val bw = maxX - minX + 1; val bh = maxY - minY + 1
        // região esparsa dentro do bbox = vazou por calhas/frestas, não é balão
        if (count < minFillRatio * bw * bh) return null
        return Bounds(minX, minY, maxX + 1, maxY + 1)
    }

    private fun tryPush(
        px: IntArray, seen: BooleanArray, stack: IntArray, sp: Int,
        idx: Int, base: Int, tol: Int
    ): Int {
        if (seen[idx]) return sp
        if (kotlin.math.abs(lum(px[idx]) - base) > tol) return sp
        seen[idx] = true
        stack[sp] = idx
        return sp + 1
    }

    /** Se o toque caiu em pixel escuro cercado de claro (texto no balão), acha claro perto. */
    private fun adjustSeed(px: IntArray, w: Int, h: Int, sx: Int, sy: Int): Int? {
        val here = lum(px[sy * w + sx])
        if (here >= 128) return null
        val r = 12
        var sum = 0; var cnt = 0
        var bestIdx = -1; var bestLum = here
        for (dy in -r..r) for (dx in -r..r) {
            val x = sx + dx; val y = sy + dy
            if (x !in 0 until w || y !in 0 until h) continue
            val l = lum(px[y * w + x]); sum += l; cnt++
            if (l > bestLum) { bestLum = l; bestIdx = y * w + x }
        }
        val mean = if (cnt > 0) sum / cnt else here
        // vizinhança majoritariamente clara e achamos um pixel claro -> re-semeia
        return if (mean > 150 && bestLum > 180 && bestIdx >= 0) bestIdx else null
    }

    private fun lum(c: Int): Int {
        val r = (c ushr 16) and 0xFF
        val g = (c ushr 8) and 0xFF
        val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun decodeScaled(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, WORK_MAX)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun sampleFor(w: Int, h: Int, target: Int): Int {
        var s = 1
        while (maxOf(w, h) / s > target) s *= 2
        return s
    }
}
