package com.coinglass.intel.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("intel_settings")

data class UserSettings(
    val liqAlertUsd: Double = 250_000.0,
    val whaleAlertUsd: Double = 1_000_000.0,
    val notificationsEnabled: Boolean = true,
    val scoreAlertAbs: Double = 30.0,
    val staleSeconds: Int = 15,
    val darkTheme: Boolean = true,
    val serviceEnabled: Boolean = false,
    val lastSymbol: String = "",
    val equityUsd: Double = 1_000.0,
    val riskPct: Double = 1.0,
    val onboardDone: Boolean = false,
    val opportunityNotify: Boolean = false,
)

class SettingsStore(private val ctx: Context) {
    private val LIQ = doublePreferencesKey("liq_alert")
    private val WHALE = doublePreferencesKey("whale_alert")
    private val NOTIF = booleanPreferencesKey("notif")
    private val SCORE = doublePreferencesKey("score_alert")
    private val STALE = intPreferencesKey("stale_sec")
    private val DARK = booleanPreferencesKey("dark")
    private val SVC = booleanPreferencesKey("svc")
    private val LAST = stringPreferencesKey("last_symbol")
    private val EQ = doublePreferencesKey("equity_usd")
    private val RISK = doublePreferencesKey("risk_pct")
    private val ONB = booleanPreferencesKey("onboard_done")
    private val OPP = booleanPreferencesKey("opp_notify")

    val flow: Flow<UserSettings> = ctx.dataStore.data.map { p ->
        UserSettings(
            liqAlertUsd = p[LIQ] ?: 250_000.0,
            whaleAlertUsd = p[WHALE] ?: 1_000_000.0,
            notificationsEnabled = p[NOTIF] ?: true,
            scoreAlertAbs = p[SCORE] ?: 30.0,
            staleSeconds = p[STALE] ?: 15,
            darkTheme = p[DARK] ?: true,
            serviceEnabled = p[SVC] ?: false,
            lastSymbol = p[LAST] ?: "",
            equityUsd = p[EQ] ?: 1_000.0,
            riskPct = p[RISK] ?: 1.0,
            onboardDone = p[ONB] ?: false,
            opportunityNotify = p[OPP] ?: false,
        )
    }

    suspend fun update(block: (UserSettings) -> UserSettings) {
        ctx.dataStore.edit { p ->
            val cur = UserSettings(
                liqAlertUsd = p[LIQ] ?: 250_000.0,
                whaleAlertUsd = p[WHALE] ?: 1_000_000.0,
                notificationsEnabled = p[NOTIF] ?: true,
                scoreAlertAbs = p[SCORE] ?: 30.0,
                staleSeconds = p[STALE] ?: 15,
                darkTheme = p[DARK] ?: true,
                serviceEnabled = p[SVC] ?: false,
                lastSymbol = p[LAST] ?: "",
                equityUsd = p[EQ] ?: 1_000.0,
                riskPct = p[RISK] ?: 1.0,
                onboardDone = p[ONB] ?: false,
                opportunityNotify = p[OPP] ?: false,
            )
            val n = block(cur)
            p[LIQ] = n.liqAlertUsd
            p[WHALE] = n.whaleAlertUsd
            p[NOTIF] = n.notificationsEnabled
            p[SCORE] = n.scoreAlertAbs
            p[STALE] = n.staleSeconds
            p[DARK] = n.darkTheme
            p[SVC] = n.serviceEnabled
            p[LAST] = n.lastSymbol
            p[EQ] = n.equityUsd
            p[RISK] = n.riskPct
            p[ONB] = n.onboardDone
            p[OPP] = n.opportunityNotify
        }
    }
}
