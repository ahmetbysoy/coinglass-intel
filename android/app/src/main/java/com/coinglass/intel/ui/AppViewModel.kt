package com.coinglass.intel.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coinglass.intel.IntelApp
import com.coinglass.intel.alert.AlertService
import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.db.DiscoverySnapEntity
import com.coinglass.intel.data.db.ScoreSnapEntity
import com.coinglass.intel.data.db.WatchEntity
import com.coinglass.intel.data.outcome.OutcomeTracker
import com.coinglass.intel.data.repo.MarketRepository
import com.coinglass.intel.data.settings.SettingsStore
import com.coinglass.intel.data.settings.UserSettings
import com.coinglass.intel.domain.Symbols
import com.coinglass.intel.domain.model.HitRate
import com.coinglass.intel.domain.model.IntelUiState
import com.coinglass.intel.data.db.OutcomeEntity
import com.coinglass.intel.data.db.PaperTradeEntity
import com.coinglass.intel.data.paper.PaperBook
import com.coinglass.intel.work.MarketDiscovery
import com.coinglass.intel.work.ScoreWorker
import com.coinglass.intel.work.WatchlistScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val intel = app as IntelApp
    private val db = AppDb.get(app)
    private val settingsStore = SettingsStore(app)
    private val repo = MarketRepository(intel.wsClient, intel.restClient, viewModelScope)
    private val scanner = WatchlistScanner(intel.restClient, db)
    private val discovery = MarketDiscovery(intel.restClient, db)
    private val tracker = OutcomeTracker(db)
    private val papers = PaperBook(db)
    private val alarmsBook = com.coinglass.intel.data.alarm.AlarmBook(db)

    val settings: StateFlow<UserSettings> = settingsStore.flow.stateIn(
        viewModelScope, SharingStarted.Eagerly, UserSettings(),
    )
    val watchlist: StateFlow<List<WatchEntity>> = db.watch().observe().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val snaps: StateFlow<List<ScoreSnapEntity>> = db.snap().observe().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val discoverySnaps: StateFlow<List<DiscoverySnapEntity>> = db.discovery().observe().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    private val hit = MutableStateFlow(HitRate())
    val outcomes: StateFlow<List<OutcomeEntity>> = db.outcome().observe(80).stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val paperTrades: StateFlow<List<PaperTradeEntity>> = db.paper().observe().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val alarms: StateFlow<List<com.coinglass.intel.data.db.AlarmEntity>> = alarmsBook.observe().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val compare = MutableStateFlow<List<String>>(emptyList())

    val live: StateFlow<IntelUiState> = combine(repo.state, watchlist, settings, hit) { st, w, cfg, h ->
        val chips = w.map { it.symbol }
        val lim = cfg.staleSeconds * 1000L
        val nowMs = System.currentTimeMillis()
        st.copy(
            chips = chips,
            inWatchlist = st.symbol.isNotBlank() && chips.contains(st.symbol),
            stale = st.symbol.isNotBlank() && st.lastUpdateMs > 0 && nowMs - st.lastUpdateMs > lim,
            hit = h,
            restErrors = st.restErrors,
            liqSeen = st.liqSeen,
            fresh = st.fresh,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, IntelUiState())

    val scanning = MutableStateFlow(false)
    val tab = MutableStateFlow(0)
    val now = MutableStateFlow(System.currentTimeMillis())

    init {
        ScoreWorker.enqueue(app)
        viewModelScope.launch {
            settingsStore.flow.collect { cfg ->
                if (cfg.lastSymbol.isNotBlank() && repo.state.value.symbol.isBlank()) {
                    repo.watch(cfg.lastSymbol)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                now.value = System.currentTimeMillis()
            }
        }
        viewModelScope.launch {
            runCatching { discovery.discover() }
        }
        viewModelScope.launch {
            repo.state.collect { st ->
                val r = st.report ?: return@collect
                tracker.record(r)
                tracker.settle(r.symbol, r.price)
                papers.settle(r.symbol, r.price)
                if (settings.value.autoPaper) papers.tryOpen(r, "auto")
                if (settings.value.notificationsEnabled && r.symbol.isNotBlank()) {
                    val nowMs = System.currentTimeMillis()
                    val liveQ = com.coinglass.intel.domain.AlarmQuote(r.symbol, r.price, r.totalScore, r.funding)
                    val hits = alarmsBook.evaluate(emptyList(), liveQ, nowMs)
                    alarmsBook.notifyHits(getApplication(), hits, nowMs)
                }
                hit.value = tracker.hitRate(r.symbol)
                repo.setBoost(tracker.alignedBoost())
            }
        }
    }

    fun toggleCompare(symbol: String) {
        val cur = compare.value.toMutableList()
        if (symbol in cur) cur.remove(symbol)
        else {
            if (cur.size >= 2) cur.removeAt(0)
            cur += symbol
        }
        compare.value = cur
    }

    fun submit(raw: String) {
        val s = Symbols.normalize(raw)
        if (s.isBlank()) return
        repo.watch(s)
        viewModelScope.launch { settingsStore.update { it.copy(lastSymbol = s) } }
    }

    fun toggleWatch(symbol: String) {
        val s = Symbols.normalize(symbol)
        if (s.isBlank()) return
        viewModelScope.launch {
            val has = db.watch().all().any { it.symbol == s }
            if (has) {
                db.watch().delete(s)
                db.snap().delete(s)
            } else {
                db.watch().upsert(WatchEntity(s))
            }
        }
    }

    fun removeWatch(symbol: String) {
        viewModelScope.launch {
            db.watch().delete(symbol)
            db.snap().delete(symbol)
        }
    }

    fun refreshScanner() {
        viewModelScope.launch {
            scanning.value = true
            runCatching { scanner.scanAll() }
            runCatching { discovery.discover() }
            scanning.value = false
        }
    }

    fun updateSettings(block: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsStore.update(block) }
    }

    fun toggleService(on: Boolean) {
        val ctx = getApplication<Application>()
        viewModelScope.launch { settingsStore.update { it.copy(serviceEnabled = on) } }
        val i = Intent(ctx, AlertService::class.java)
        if (on) {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } else {
            ctx.stopService(i)
        }
    }

    fun openPaper() {
        val r = live.value.report ?: return
        viewModelScope.launch { papers.tryOpen(r, "manual") }
    }

    fun selectChartTf(tf: String) {
        repo.setChartTf(tf)
    }

    fun selectTab(i: Int) {
        tab.value = i
    }

    fun cycleWatch(delta: Int) {
        val list = watchlist.value.map { it.symbol }
        if (list.isEmpty()) return
        val cur = live.value.symbol
        val i = list.indexOf(cur).let { if (it < 0) 0 else it }
        submit(list[(i + delta + list.size) % list.size])
    }

    override fun onCleared() {
        repo.stop()
        super.onCleared()
    }
}
