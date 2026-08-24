package com.coinglass.intel.ui

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle

class AxisLabelCache(private val cap: Int = 64) {
    private val map = LinkedHashMap<String, TextLayoutResult>(cap, 0.75f, true)

    fun measure(measurer: TextMeasurer, text: String, style: TextStyle): TextLayoutResult {
        val key = text + "|" + style.fontSize.value + "|" + style.color.value
        map[key]?.let { return it }
        val got = measurer.measure(text, style)
        if (map.size >= cap) map.remove(map.keys.first())
        map[key] = got
        return got
    }
}
