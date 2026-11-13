package com.parihardigitallabs.dailysathi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EverydayCalculationsTest {
    @Test fun discountCalculatesFinalPrice() {
        assertEquals(850.0, EverydayCalculations.discountedPrice(1000.0, 15.0)!!, 0.0001)
        assertEquals(0.0, EverydayCalculations.discountedPrice(1000.0, 100.0)!!, 0.0001)
        assertEquals(1000.0, EverydayCalculations.discountedPrice(1000.0, 0.0)!!, 0.0001)
    }

    @Test fun discountRejectsInvalidInputs() {
        assertNull(EverydayCalculations.discountedPrice(-1.0, 10.0))
        assertNull(EverydayCalculations.discountedPrice(100.0, 101.0))
        assertNull(EverydayCalculations.discountedPrice(Double.NaN, 10.0))
        assertNull(EverydayCalculations.discountedPrice(100.0, null))
    }

    @Test fun gstCalculatesTaxAndGrandTotal() {
        val result = EverydayCalculations.gstBreakdown(1000.0, 18.0)!!
        assertEquals(180.0, result.gstAmount, 0.0001)
        assertEquals(1180.0, result.total, 0.0001)
    }

    @Test fun gstRejectsInvalidRatesAndAmounts() {
        assertNull(EverydayCalculations.gstBreakdown(100.0, -1.0))
        assertNull(EverydayCalculations.gstBreakdown(100.0, 101.0))
        assertNull(EverydayCalculations.gstBreakdown(-1.0, 18.0))
        assertNull(EverydayCalculations.gstBreakdown(100.0, Double.POSITIVE_INFINITY))
    }

    @Test fun percentageCalculatesFractionOfAmount() {
        assertEquals(25.0, EverydayCalculations.percentageOf(200.0, 12.5)!!, 0.0001)
        assertEquals(0.0, EverydayCalculations.percentageOf(0.0, 50.0)!!, 0.0001)
    }

    @Test fun metricConversionsScaleByOneThousand() {
        val result = EverydayCalculations.metricConversions(1.25)!!
        assertEquals(1250.0, result.grams, 0.0001)
        assertEquals(1250.0, result.metres, 0.0001)
        assertEquals(1250.0, result.millilitres, 0.0001)
        assertNull(EverydayCalculations.metricConversions(-0.1))
        assertNull(EverydayCalculations.metricConversions(Double.NaN))
    }
}
