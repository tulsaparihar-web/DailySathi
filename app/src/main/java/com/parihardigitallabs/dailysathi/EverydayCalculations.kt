package com.parihardigitallabs.dailysathi

internal data class GstBreakdown(val gstAmount: Double, val total: Double)
internal data class MetricConversions(val grams: Double, val metres: Double, val millilitres: Double)

internal object EverydayCalculations {
    fun discountedPrice(amount: Double?, discountPercent: Double?): Double? {
        if (amount == null || discountPercent == null || !amount.isFinite() ||
            !discountPercent.isFinite() || amount < 0.0 || discountPercent !in 0.0..100.0
        ) return null
        return amount * (1.0 - discountPercent / 100.0)
    }

    fun gstBreakdown(amount: Double?, ratePercent: Double?): GstBreakdown? {
        if (amount == null || ratePercent == null || !amount.isFinite() ||
            !ratePercent.isFinite() || amount < 0.0 || ratePercent !in 0.0..100.0
        ) return null
        val gst = amount * ratePercent / 100.0
        return GstBreakdown(gstAmount = gst, total = amount + gst)
    }

    fun percentageOf(amount: Double?, percent: Double?): Double? {
        if (amount == null || percent == null || !amount.isFinite() || !percent.isFinite()) return null
        return amount * percent / 100.0
    }

    fun metricConversions(value: Double?): MetricConversions? {
        if (value == null || !value.isFinite() || value < 0.0) return null
        val converted = value * 1000.0
        return MetricConversions(grams = converted, metres = converted, millilitres = converted)
    }
}
