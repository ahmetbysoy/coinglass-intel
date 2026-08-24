package com.coinglass.intel.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.math.abs

val JsonX = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

fun toFloat(v: Any?, default: Double = 0.0): Double = when (v) {
    null -> default
    is Number -> v.toDouble()
    is Boolean -> if (v) 1.0 else 0.0
    is String -> v.toDoubleOrNull() ?: default
    is JsonPrimitive -> v.doubleOrNull ?: v.content.toDoubleOrNull() ?: default
    is JsonElement -> toFloat(v.asPrimitiveOrNull(), default)
    else -> default
}

fun JsonElement.asPrimitiveOrNull(): JsonPrimitive? = when (this) {
    is JsonPrimitive -> this
    else -> null
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray

fun JsonObject.opt(key: String): JsonElement? = this[key]?.takeUnless { it is JsonNull }

fun JsonElement?.path(vararg keys: Any): JsonElement? {
    var cur: JsonElement? = this
    for (k in keys) {
        cur = when {
            cur == null || cur is JsonNull -> return null
            k is Int && cur is JsonArray -> cur.getOrNull(k)
            k is String && cur is JsonObject -> cur[k]
            else -> return null
        }
    }
    return cur?.takeUnless { it is JsonNull }
}

fun JsonElement?.asDouble(default: Double = 0.0): Double {
    val el = this ?: return default
    return when (el) {
        is JsonPrimitive -> el.doubleOrNull ?: el.content.toDoubleOrNull() ?: default
        else -> default
    }
}

fun JsonElement?.asString(default: String = ""): String {
    val el = this ?: return default
    return when (el) {
        is JsonPrimitive -> el.content
        else -> default
    }
}

fun JsonElement?.asObj(): JsonObject? = this as? JsonObject
fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

fun fmtPrice(p: Double?): String {
    val v = p ?: 0.0
    if (v == 0.0) return "$0"
    return when {
        v >= 1000 -> "$" + "%,.2f".format(Locale.US, v)
        v >= 1 -> "$" + "%.4f".format(Locale.US, v)
        v >= 0.01 -> "$" + "%.6f".format(Locale.US, v)
        v >= 0.0001 -> "$" + "%.8f".format(Locale.US, v)
        else -> "$" + "%.10f".format(Locale.US, v)
    }
}

fun fmtUsd(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1_000_000_000 -> "$" + "%.2fB".format(Locale.US, v / 1_000_000_000)
        a >= 1_000_000 -> "$" + "%.2fM".format(Locale.US, v / 1_000_000)
        a >= 1_000 -> "$" + "%.1fK".format(Locale.US, v / 1_000)
        else -> "$" + "%.0f".format(Locale.US, v)
    }
}

fun JsonObject.str(vararg keys: String): String {
    for (k in keys) {
        val v = this[k]
        if (v is JsonPrimitive && v.content.isNotBlank()) return v.content
    }
    return ""
}

fun JsonObject.num(vararg keys: String): Double {
    for (k in keys) {
        val n = this[k].asDouble(Double.NaN)
        if (!n.isNaN()) return n
    }
    return 0.0
}
