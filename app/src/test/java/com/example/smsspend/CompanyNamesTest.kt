package com.example.smsspend

import com.example.smsspend.parser.CompanyNames
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanyNamesTest {

    @Test fun mergesEnglishAndArabicAndTruncations() {
        val canon = "OQ Exploration & Production"
        assertEquals(canon, CompanyNames.canonical("OQ EXPLORATIO"))      // bank truncation
        assertEquals(canon, CompanyNames.canonical("Oq Exploration"))     // title-cased
        assertEquals(canon, CompanyNames.canonical("اوكيو للاستكشاف"))     // Arabic (MCD)
        assertEquals(canon, CompanyNames.canonical("OQEP"))               // ticker
    }

    @Test fun leavesUnknownNamesUnchanged() {
        assertEquals("Talabat", CompanyNames.canonical("Talabat"))
        assertEquals("Some Local Shop", CompanyNames.canonical("Some Local Shop"))
    }
}
