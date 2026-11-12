package com.bubbl.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PageSliderTest {

    @Test fun ltrMapsIndexToOneBasedValue() {
        assertEquals(1f, PageSlider.valueFor(0, 10, rtl = false), 0f)
        assertEquals(10f, PageSlider.valueFor(9, 10, rtl = false), 0f)
        assertEquals(0, PageSlider.indexFor(1f, 10, rtl = false))
        assertEquals(9, PageSlider.indexFor(10f, 10, rtl = false))
    }

    @Test fun rtlInvertsSlider() {
        // página 1 na ponta direita; arrastar p/ a direita volta páginas
        assertEquals(10f, PageSlider.valueFor(0, 10, rtl = true), 0f)
        assertEquals(1f, PageSlider.valueFor(9, 10, rtl = true), 0f)
        assertEquals(0, PageSlider.indexFor(10f, 10, rtl = true))
        assertEquals(9, PageSlider.indexFor(1f, 10, rtl = true))
        assert(PageSlider.indexFor(8f, 10, rtl = true) < PageSlider.indexFor(7f, 10, rtl = true))
    }

    @Test fun roundTripBothDirections() {
        for (rtl in listOf(false, true)) {
            for (i in 0 until 7) {
                assertEquals(i, PageSlider.indexFor(PageSlider.valueFor(i, 7, rtl), 7, rtl))
            }
        }
    }

    @Test fun valuesStayInSliderRange() {
        for (rtl in listOf(false, true)) {
            for (i in -2 until 12) {
                val v = PageSlider.valueFor(i, 10, rtl)
                assert(v in 1f..10f) { "valor $v fora de 1..10 (i=$i, rtl=$rtl)" }
            }
        }
    }

    @Test fun indexClampsOutOfRangeAndRounds() {
        assertEquals(0, PageSlider.indexFor(0f, 10, rtl = false))
        assertEquals(9, PageSlider.indexFor(50f, 10, rtl = false))
        assertEquals(2, PageSlider.indexFor(2.6f, 10, rtl = false))
    }
}
