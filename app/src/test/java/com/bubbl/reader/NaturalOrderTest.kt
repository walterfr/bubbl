package com.bubbl.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderTest {
    @Test fun sortsPagesNumerically() {
        val input = listOf("p10.jpg", "p2.jpg", "p1.jpg", "p10a.jpg")
        val sorted = input.sortedWith(PageLoader.naturalOrder)
        assertEquals(listOf("p1.jpg", "p2.jpg", "p10.jpg", "p10a.jpg"), sorted)
    }

    @Test fun leadingZerosCompareByValue() {
        // 01 e 1 têm mesmo valor (empate); 2 vem depois; 10 por último
        assertEquals(0, PageLoader.compareNatural("01.jpg", "1.jpg"))
        assert(PageLoader.compareNatural("1.jpg", "2.jpg") < 0)
        assert(PageLoader.compareNatural("02.jpg", "10.jpg") < 0)
    }

    @Test fun deepFolderPathsCompare() {
        val a = "vol1/ch2/003.png"
        val b = "vol1/ch10/001.png"
        assert(PageLoader.compareNatural(a, b) < 0) // ch2 antes de ch10
    }
}
