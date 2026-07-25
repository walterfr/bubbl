package com.bubbl.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
