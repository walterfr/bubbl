package com.bubbl.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsTest {

    private fun entry(key: String, last: Long, page: Int = 0, total: Int = 10, rtl: Boolean = false) =
        BookEntry(key, folder = false, title = key, page = page, total = total, rtl = rtl, lastOpened = last)

    @Test fun normalizeSortsByLastOpenedDesc() {
        val list = listOf(entry("a", 1), entry("b", 3), entry("c", 2))
        assertEquals(listOf("b", "c", "a"), Recents.normalize(list).map { it.key })
    }

    @Test fun normalizeKeepsMostRecentPerKey() {
        val list = listOf(entry("a", 1, page = 2), entry("a", 5, page = 7), entry("b", 3))
        val out = Recents.normalize(list)
        assertEquals(listOf("a", "b"), out.map { it.key })
        assertEquals(7, out.first().page)
    }

    @Test fun normalizeLimitsToMax() {
        val list = (1..15).map { entry("k$it", it.toLong()) }
        val out = Recents.normalize(list)
        assertEquals(Recents.MAX, out.size)
        assertEquals("k15", out.first().key)
        assertEquals("k6", out.last().key)
    }

    @Test fun upsertReplacesAndMovesToTop() {
        val list = listOf(entry("a", 10), entry("b", 5))
        val out = Recents.upsert(list, entry("b", 20, page = 3))
        assertEquals(listOf("b", "a"), out.map { it.key })
        assertEquals(3, out.first().page)
    }

    @Test fun upsertNewWinsOnTimestampTie() {
        val out = Recents.upsert(listOf(entry("a", 10, page = 1)), entry("a", 10, page = 4))
        assertEquals(1, out.size)
        assertEquals(4, out.single().page)
    }

    @Test fun upsertDropsOldestBeyondMax() {
        val list = (1..Recents.MAX).map { entry("k$it", it.toLong()) }
        val out = Recents.upsert(list, entry("new", 100))
        assertEquals(Recents.MAX, out.size)
        assertEquals("new", out.first().key)
        assertFalse(out.any { it.key == "k1" })
    }

    @Test fun removeByKey() {
        val out = Recents.remove(listOf(entry("a", 1), entry("b", 2)), "a")
        assertEquals(listOf("b"), out.map { it.key })
    }

    @Test fun openedPreservesPageAndDirection() {
        val old = entry("a", 1, page = 4, total = 10, rtl = true)
        val e = Recents.opened(old, "a", folder = false, title = "A", total = 10, now = 99)
        assertEquals(4, e.page)
        assertTrue(e.rtl)
        assertEquals(99L, e.lastOpened)
        assertEquals("A", e.title)
    }

    @Test fun openedNewBookStartsAtFirstPageLtr() {
        val e = Recents.opened(null, "a", folder = true, title = "A", total = 5, now = 1)
        assertEquals(0, e.page)
        assertFalse(e.rtl)
        assertTrue(e.folder)
    }

    @Test fun openedClampsPageWhenBookShrank() {
        val old = entry("a", 1, page = 30, total = 40)
        assertEquals(9, Recents.opened(old, "a", false, "A", total = 10, now = 2).page)
    }

    @Test fun clampPage() {
        assertEquals(0, Recents.clampPage(-3, 10))
        assertEquals(9, Recents.clampPage(12, 10))
        assertEquals(0, Recents.clampPage(5, 0))
    }
}
