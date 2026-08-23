package com.coinglass.intel.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class LockstepTest {
    private val root = Json.parseToJsonElement(
        javaClass.classLoader!!.getResourceAsStream("lockstep.json")!!.bufferedReader().readText(),
    ).jsonObject

    private fun n(obj: kotlinx.serialization.json.JsonObject, key: String): Double =
        obj.getValue(key).jsonPrimitive.double

    @Test
    fun rsiMatchesFixture() {
        for (el in root.getValue("rsi").jsonArray) {
            val o = el.jsonObject
            assertNear(n(o, "out"), Curves.rsiSignal(n(o, "in")))
        }
    }

    @Test
    fun oiMatchesFixture() {
        for (el in root.getValue("oi").jsonArray) {
            val o = el.jsonObject
            assertNear(n(o, "out"), Curves.oiScore(n(o, "oi"), n(o, "chg")))
        }
    }

    @Test
    fun lsMatchesFixture() {
        for (el in root.getValue("ls").jsonArray) {
            val o = el.jsonObject
            assertNear(n(o, "out"), Curves.lsScore(n(o, "ls")))
        }
    }

    @Test
    fun riskMatchesFixture() {
        for (el in root.getValue("risk").jsonArray) {
            val o = el.jsonObject
            val got = Curves.riskScore(n(o, "atr"), n(o, "fund"), n(o, "ls"), n(o, "vol"))
            assertEquals(n(o, "out").toInt(), got)
        }
    }

    @Test
    fun momMatchesFixture() {
        for (el in root.getValue("mom").jsonArray) {
            val o = el.jsonObject
            assertNear(n(o, "out"), Curves.momFromRsiAndRet(n(o, "rsi"), n(o, "ret3")))
        }
    }

    private fun assertNear(exp: Double, got: Double) {
        assertEquals(exp, got, 1e-6)
        org.junit.Assert.assertTrue(abs(exp - got) < 1e-6)
    }
}
