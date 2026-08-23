package com.coinglass.intel.domain

import kotlin.math.tanh

/** Online boost from settled aligned returns. n<8 none, 8-29 half, ≥30 full. */
object WeightCalibrator {
    const val MIN_N = 8
    const val FULL_N = 30

    fun boost(alignedAvg: Map<String, Double>, n: Int): Map<String, Double> {
        if (n < MIN_N) return emptyMap()
        val scale = if (n < FULL_N) 0.175 else 0.35
        return alignedAvg.mapValues { (_, v) -> 1.0 + scale * tanh(v / 2.0) }
    }

    fun apply(base: MutableMap<String, Double>, boost: Map<String, Double>): MutableMap<String, Double> {
        for ((k, b) in boost) {
            if (base.containsKey(k)) base[k] = base.getValue(k) * b
        }
        val tot = base.values.sum().let { if (it == 0.0) 1.0 else it }
        for (k in base.keys.toList()) base[k] = base.getValue(k) / tot * 100.0
        return base
    }

    /** Map component keys → ensembleTf signal names so both scorers share one source. */
    fun toEnsemble(compWeights: Map<String, Double>): Map<String, Double> {
        val map = mapOf(
            "OB" to "ob_imbalance",
            "TF" to "whale_flow",
            "OI" to "oi_momentum",
            "Funding" to "funding_signal",
            "Liq" to "liq_pressure",
            "Vol" to "volume_signal",
            "Mom" to "volume_signal",
        )
        val out = mutableMapOf<String, Double>()
        for ((k, sig) in map) {
            val w = compWeights[k] ?: continue
            out[sig] = (out[sig] ?: 0.0) + w
        }
        val tot = out.values.sum().let { if (it == 0.0) 1.0 else it }
        return out.mapValues { it.value / tot }
    }
}
