package com.bubbl.reader

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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

    private const val DILATE = 3  // px (escala reduzida) p/ englobar o contorno do balão

    /** Bounding box puro (sem android.graphics, p/ ser testável em JVM). */
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** Resultado: retângulo na resolução original + máscara alpha no formato do balão. */
    class Detection(val rect: Rect, val mask: Bitmap)

    /** Roda em thread de background. srcW/srcH = dimensões reais da página. */
    fun detect(
        resolver: ContentResolver, uri: Uri,
        srcX: Float, srcY: Float, srcW: Int, srcH: Int
    ): Detection? {
        if (srcW <= 0 || srcH <= 0) return null
        val small = decodeScaled(resolver, uri) ?: return null
        try {
            val w = small.width; val h = small.height
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)

            var sx = (srcX / srcW * w).toInt().coerceIn(0, w - 1)
            var sy = (srcY / srcH * h).toInt().coerceIn(0, h - 1)
            adjustSeed(px, w, h, sx, sy)?.let { sx = it % w; sy = it / w }

            val seen = BooleanArray(w * h)
            val box = floodFillBounds(px, w, h, sx, sy, TOL, MAX_FILL_FRAC, MIN_FILL_RATIO, seen)
                ?: return null

            // expande o bbox p/ dar espaço ao contorno (dilatação)
            val ex0 = (box.left - DILATE).coerceAtLeast(0)
            val ey0 = (box.top - DILATE).coerceAtLeast(0)
            val ex1 = (box.right + DILATE).coerceAtMost(w)
            val ey1 = (box.bottom + DILATE).coerceAtMost(h)
            val bw = ex1 - ex0; val bh = ey1 - ey0
            if (bw < 4 || bh < 4) return null

            // máscara: opaca onde a região (dilatada) cobre = formato do balão
            val maskPx = IntArray(bw * bh)
            for (yy in 0 until bh) for (xx in 0 until bw) {
                maskPx[yy * bw + xx] = if (dilatedAt(seen, w, h, ex0 + xx, ey0 + yy, DILATE)) -1 else 0
            }
            val mask = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            mask.setPixels(maskPx, 0, bw, 0, 0, bw, bh)

            val fx = srcW.toFloat() / w; val fy = srcH.toFloat() / h
            val rect = Rect(
                (ex0 * fx).toInt().coerceIn(0, srcW - 1),
                (ey0 * fy).toInt().coerceIn(0, srcH - 1),
                (ex1 * fx).toInt().coerceIn(1, srcW),
                (ey1 * fy).toInt().coerceIn(1, srcH)
            )
            if (rect.width() <= 8 || rect.height() <= 8) { mask.recycle(); return null }
            return Detection(rect, mask)
        } finally {
            small.recycle()
        }
    }

    private fun dilatedAt(seen: BooleanArray, w: Int, h: Int, gx: Int, gy: Int, r: Int): Boolean {
        var dy = -r
        while (dy <= r) {
            var dx = -r
            while (dx <= r) {
                val nx = gx + dx; val ny = gy + dy
                if (nx in 0 until w && ny in 0 until h && seen[ny * w + nx]) return true
                dx++
            }
            dy++
        }
        return false
    }

    /** Recorte no formato do balão: recorte nítido do bbox mascarado pela silhueta. */
    fun shapedCrop(resolver: ContentResolver, uri: Uri, det: Detection): Bitmap? {
        val crop = crop(resolver, uri, det.rect)
        if (crop == null) { det.mask.recycle(); return null }
        val scaledMask = Bitmap.createScaledBitmap(det.mask, crop.width, crop.height, true)
        val out = Bitmap.createBitmap(crop.width, crop.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawBitmap(crop, 0f, 0f, null)
            drawBitmap(scaledMask, 0f, 0f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                })
        }
        crop.recycle(); scaledMask.recycle(); det.mask.recycle()
        return out
    }

    /** Recorte retangular nítido do balão em alta resolução. */
    private fun crop(resolver: ContentResolver, uri: Uri, rect: Rect): Bitmap? {
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
        tol: Int, maxFrac: Float, minFillRatio: Float = 0f,
        outSeen: BooleanArray? = null
    ): Bounds? {
        val n = w * h
        if (sx !in 0 until w || sy !in 0 until h) return null
        val base = lum(px[sy * w + sx])
        val seen = outSeen ?: BooleanArray(n)
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
