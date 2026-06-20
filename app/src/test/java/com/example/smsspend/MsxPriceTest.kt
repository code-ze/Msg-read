package com.example.smsspend

import com.example.smsspend.data.MsxPriceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MsxPriceTest {

    @Test fun extractsPriceAfterSymbol() {
        val html = "<tr><td>OQEP</td><td>0.142</td><td>+1.2%</td></tr>"
        assertEquals(0.142, MsxPriceSource.extractPrice(html, "OQEP")!!, 0.0001)
    }

    @Test fun returnsNullWhenSymbolMissing() {
        val html = "<tr><td>OQGN</td><td>0.110</td></tr>"
        assertNull(MsxPriceSource.extractPrice(html, "OQEP"))
    }
}
