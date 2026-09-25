package com.bubbl.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalloonDetectorTest {

    private val WHITE = 0xFFFFFFFF.toInt()
    private val BLACK = 0xFF000000.toInt()

    /** Retângulo branco (balão) sobre fundo preto: bbox deve casar com o retângulo. */
    @Test fun findsWhiteRectBounds() {
        val w = 40; val h = 40
        val px = IntArray(w * h) { BLACK }
        // retângulo branco x=[10,29], y=[8,27]
        for (y in 8..27) for (x in 10..29) px[y * w + x] = WHITE

        val box = BalloonDetector.floodFillBounds(px, w, h, 15, 15, 45, 0.5f)
        assertNotNull(box)
        assertEquals(10, box!!.left)
        assertEquals(8, box.top)
        assertEquals(30, box.right)   // maxX 29 + 1
        assertEquals(28, box.bottom)  // maxY 27 + 1
    }

    /** Texto escuro (buraco) dentro do balão não impede o bbox de cobrir o balão. */
    @Test fun holesInsideDoNotBreakBounds() {
        val w = 40; val h = 40
        val px = IntArray(w * h) { BLACK }
        for (y in 8..27) for (x in 10..29) px[y * w + x] = WHITE
        px[15 * w + 15] = BLACK // "letra" isolada no meio

        val box = BalloonDetector.floodFillBounds(px, w, h, 12, 12, 45, 0.5f)!!
        assertEquals(10, box.left); assertEquals(8, box.top)
        assertEquals(30, box.right); assertEquals(28, box.bottom)
    }

    /** Imagem uniforme: região vaza p/ tudo (encosta nas 4 bordas) -> null. */
    @Test fun uniformImageSpillsToNull() {
        val w = 30; val h = 30
        val px = IntArray(w * h) { WHITE }
        assertNull(BalloonDetector.floodFillBounds(px, w, h, 15, 15, 45, 0.5f))
    }

    /** Região local de balão deve ser aceita mesmo quando a imagem inteira é muito maior. */
    @Test fun localBalloonBoundsFindsRectAroundTouch() {
        val w = 80; val h = 80
        val px = IntArray(w * h) { BLACK }
        for (y in 24..54) for (x in 22..58) px[y * w + x] = WHITE

        val box = BalloonDetector.localBalloonBounds(px, w, h, 40, 40, 45, 0.35f, 0.4f)
        assertNotNull(box)
        assertEquals(22, box!!.left)
        assertEquals(24, box.top)
        assertEquals(59, box.right)
        assertEquals(55, box.bottom)
    }

    /** Fundo uniforme não pode vazar por se destacar como balão no raio local. */
    @Test fun localBalloonBoundsRejectsUniformBackground() {
        val w = 80; val h = 80
        val px = IntArray(w * h) { WHITE }
        assertNull(BalloonDetector.localBalloonBounds(px, w, h, 40, 40, 45, 0.35f, 0.4f))
    }

    /** Regressão: a máscara nunca pode sair vazia, seja qual for o detector do bbox. */
    @Test fun regionMaskCoversBalloonInsideBox() {
        val w = 40; val h = 40
        val px = IntArray(w * h) { BLACK }
        for (y in 8..27) for (x in 10..29) px[y * w + x] = WHITE
        val box = BalloonDetector.Bounds(10, 8, 30, 28)

        val mask = BalloonDetector.regionMask(px, w, h, 15, 15, 45, box)
        assertTrue(mask[15 * w + 15])
        assertTrue(mask[27 * w + 29])
        assertFalse(mask[5 * w + 5])       // fora do bbox
        assertEquals(20 * 20, mask.count { it })
    }

    /** Toque em texto escuro (região esparsa no bbox): a máscara cai no bbox inteiro. */
    @Test fun regionMaskFallsBackToWholeBox() {
        val w = 40; val h = 40
        val px = IntArray(w * h) { WHITE }
        px[15 * w + 15] = BLACK
        val box = BalloonDetector.Bounds(10, 8, 30, 28)

        val mask = BalloonDetector.regionMask(px, w, h, 15, 15, 45, box)
        assertEquals(20 * 20, mask.count { it })
        assertFalse(mask[0])
    }
}
