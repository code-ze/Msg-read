package com.example.smsspend

import com.example.smsspend.parser.Categorizer
import com.example.smsspend.parser.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {

    @Test fun stripsLeadingCode() {
        assertEquals("Talabat", Categorizer.cleanMerchant("123456-TALABAT"))
    }

    @Test fun stripsTrailingBranchNumber() {
        assertEquals("Lulu", Categorizer.cleanMerchant("LULU 12345"))
    }

    @Test fun keywordCategorization() {
        assertEquals("Food Delivery", Categorizer.defaultCategory(TxnType.DEBIT, "Talabat"))
        assertEquals("Groceries", Categorizer.defaultCategory(TxnType.DEBIT, "Lulu Hyper"))
    }

    @Test fun typeDrivenCategories() {
        assertEquals(Categorizer.INVESTMENTS, Categorizer.defaultCategory(TxnType.IPO, "Anything"))
        assertEquals(Categorizer.DIVIDENDS, Categorizer.defaultCategory(TxnType.DIVIDEND, "OQ"))
        assertEquals(Categorizer.INCOME, Categorizer.defaultCategory(TxnType.DEPOSIT, "x"))
        assertEquals(Categorizer.TRANSFERS, Categorizer.defaultCategory(TxnType.WALLET_OUT, "Friend"))
    }

    @Test fun unknownFallsBackToOther() {
        assertEquals(Categorizer.OTHER, Categorizer.defaultCategory(TxnType.DEBIT, "Some Random Shop"))
    }

    @Test fun defaultSubcategorySplitsUtilities() {
        assertEquals("Electricity", Categorizer.defaultSubcategory("Utilities", "NAMA Electricity"))
        assertEquals("Water", Categorizer.defaultSubcategory("Utilities", "DIAM"))
    }

    @Test fun defaultSubcategoryEmptyWhenUnknownOrNoRules() {
        assertEquals("", Categorizer.defaultSubcategory("Utilities", "Mystery Co"))
        assertEquals("", Categorizer.defaultSubcategory("Groceries", "Lulu")) // no keyword rule for it
        assertEquals("", Categorizer.defaultSubcategory("Rent", "Landlord"))
    }
}
