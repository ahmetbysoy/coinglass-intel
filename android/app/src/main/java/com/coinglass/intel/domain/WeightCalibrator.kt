package com.coinglass.intel.domain

import kotlin.math.tanh

/** Online boost from settled aligned returns. Base weights stay the prior. */
object WeightCalibrator {
    fun boost(alignedAvg: Map<String, Double>): Map<String, Double> {
        // alignedAvg: OB/TF/OI/... → mean(fwd * sign(component))
        return alignedAvg.mapValues { (_, v) -> 1.0 + 0.35 * tanh(v / 2.0) }
    }

    fun apply(base: MutableMap<String, Double>, boost: Map<String, Double>): MutableMap<String, Double> {
        for ((k, b) in boost) {
            if (base.containsKey(k)) base[k] = base.getValue(k) * b
        }
        val tot = base.values.sum().let { if (it == 0.0) 1.0 else it }
        for (k in base.keys.toList()) base[k] = base.getValue(k) / tot * 100.0
        return base
    }
}
