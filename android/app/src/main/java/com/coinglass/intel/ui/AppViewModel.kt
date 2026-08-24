package com.coinglass.intel.ui

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coinglass.intel.IntelApp
import com.coinglass.intel.alert.AlertService
import com.coinglass.intel.data.alarm.AlarmBook
import com.coinglass.intel.data.db.AlarmEntity
import com.coinglass.intel.data.db.AppDb
import com.coinglass.intel.data.db.DiscoverySnapEntity
import com.coinglass.intel.data.db.OutcomeEntity
import com.coinglass.intel.data.db.PaperTradeEntity
import com.coinglass.intel.data.db.ScoreSnapEntity
import com.coinglass.intel.data.db.WatchEntity
import com.coinglass.intel.data.outcome.OutcomeTracker
import com.coinglass.intel.data.paper.PaperBook
import com.coinglass.intel.data.repo.MarketRepository
import com.coinglass.intel.data.settings.SettingsStore
import com.coinglass.intel.data.settings.UserSettings
import com.coinglass.intel.domain.AlarmKind
import com.coinglass.intel.domain.AlarmOp
import com.coinglass.intel.domain.AlarmQuote
import com.coinglass.intel.domain.AlarmSig
import com.coinglass.intel.domain.CompareRing
import com.coinglass.intel.domain.StaleClock
import com.coinglass.intel.domain.Symbols
import com.coinglass.intel.domain.WatchCycle
import com.coinglass.intel.domain.model.HitRate
import com.coinglass.intel.domain.model.IntelUiState
import com.coinglass.intel.work.MarketDiscovery
import com.coinglass.intel.work.ScoreWorker
import com.coinglass.intel.work.WatchlistScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App helm. UI observes; mutations stay here.
 * Deps come in through the constructor so tests can stub them.
 */
class AppViewModel(
    app: Application,
    private val savedState: SavedStateHandle,
    private val db: AppDb,
    private val settingsStore: SettingsStore,
    private val scanner: WatchlistScanner,
    private val discovery: MarketDiscovery,
    private val tracker: OutcomeTracker,
    private val papers: PaperBook,
    private val alarmsBook: AlarmBook,
    repoFactory: (CoroutineScope) -> MarketRepository,
) : AndroidViewModel(app) {

    sealed interface UiEvent {
        data class Error(val message: String) : UiEvent
        data class Info(val message: String) : UiEvent
    }

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val repo = repoFactory(viewModelScope)

    val settings: StateFlow<UserSettings> = settingsStore.flow.stateIn(
        viewModelScope, SharingStarted.Eagerly, UserSettings(),
    )
    val watchlist: StateFlow<List<WatchEntity>> = db.watch().observe().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    // Alarm pipe reads snaps.value — keep Eagerly so a Radar-only collector cannot empty it.
    val snaps: StateFlow<List<ScoreSnapEntity>> = db.snap().observe().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val discoverySnaps: StateFlow<List<DiscoverySnapEntity>> = db.discovery().observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(SUB_TIMEOUT_MS), emptyList(),
    )
    val outcomes: StateFlow<List<OutcomeEntity>> = db.outcome().observe(OUTCOME_HISTORY).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(SUB_TIMEOUT_MS), emptyList(),
    )
    val paperTrades: StateFlow<List<PaperTradeEntity>> = db.paper().observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(SUB_TIMEOUT_MS), emptyList(),
    )
    val alarms: StateFlow<List<AlarmEntity>> = alarmsBook.observe().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )

    private val hit = MutableStateFlow(HitRate())

    private val _compare = MutableStateFlow<List<String>>(emptyList())
    val compare: StateFlow<List<String>> = _compare.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val tab: StateFlow<Int> = savedState.getStateFlow(KEY_TAB, 0)

    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    val live: StateFlow<IntelUiState> =
        combine(repo.state, watchlist, settings, hit, now) { st, w, cfg, h, nowMs ->
            val chips = w.map { it.symbol }
            st.copy(
                chips = chips,
                inWatchlist = st.symbol.isNotBlank() && st.symbol in chips,
                stale = StaleClock.isStale(st.symbol, st.lastUpdateMs, nowMs, cfg.staleSeconds),
                hit = h,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, IntelUiState())

    private var lastAlarmSig = ""

    init {
        ScoreWorker.enqueue(app)
        restoreLastSymbol()
        launchInitialDiscovery()
        pipeReports()
    }

    private fun restoreLastSymbol() {
        viewModelScope.launch {
            val cfg = settingsStore.flow.first()
            if (cfg.lastSymbol.isNotBlank() && repo.state.value.symbol.isBlank()) {
                repo.watch(cfg.lastSymbol)
            }
        }
    }

    private fun launchInitialDiscovery() {
        viewModelScope.launch { discovery.runCaught("Keşif turu") }
    }

    private fun pipeReports() {
        viewModelScope.launch {
            repo.state.collect { st ->
                val r = st.report ?: return@collect
                tracker.record(r)
                tracker.settle(r.symbol, r.price)
                papers.settle(r.symbol, r.price)
                if (settings.value.autoPaper) papers.tryOpen(r, SOURCE_AUTO)

                if (settings.value.notificationsEnabled && r.symbol.isNotBlank()) {
                    val sig = AlarmSig.of(r.symbol, r.price, r.totalScore, r.funding)
                    if (sig != lastAlarmSig) {
                        lastAlarmSig = sig
                        val nowMs = System.currentTimeMillis()
                        val quote = AlarmQuote(r.symbol, r.price, r.totalScore, r.funding)
                        // First arg is extra quotes (snaps), not the alarm table — AlarmBook loads alarms from Room.
                        val quotes = snaps.value.map { AlarmBook.quoteOf(it) }
                        val hits = alarmsBook.evaluate(quotes, quote, nowMs)
                        alarmsBook.notifyHits(getApplication(), hits, nowMs)
                    }
                }

                hit.value = tracker.hitRate(r.symbol)
                repo.setBoost(tracker.alignedBoost())
            }
        }
    }

    fun toggleCompare(symbol: String) {
        _compare.value = CompareRing.toggle(_compare.value, symbol)
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
            val added = db.watch().toggle(s)
            if (!added) db.snap().delete(s)
        }
    }

    fun removeWatch(symbol: String) {
        viewModelScope.launch {
            db.watch().delete(symbol)
            db.snap().delete(symbol)
        }
    }

    fun refreshScanner() {
        if (!_scanning.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                scanner.runCaught("Watchlist taraması") { scanAll() }
                discovery.runCaught("Keşif")
            } finally {
                _scanning.value = false
            }
        }
    }

    fun updateSettings(block: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsStore.update(block) }
    }

    fun toggleService(on: Boolean) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            settingsStore.update { it.copy(serviceEnabled = on) }
            val intent = Intent(ctx, AlertService::class.java)
            if (on) {
                try {
                    ContextCompat.startForegroundService(ctx, intent)
                } catch (e: Exception) {
                    settingsStore.update { it.copy(serviceEnabled = false) }
                    val blocked = Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException
                    val msg = if (blocked) {
                        "Servis arka plandan başlatılamadı — uygulamayı açıp tekrar deneyin."
                    } else {
                        "Servis başlatılamadı: ${e.message ?: "hata"}"
                    }
                    _events.send(UiEvent.Error(msg))
                }
            } else {
                ctx.stopService(intent)
            }
        }
    }

    fun openPaper() {
        val r = live.value.report ?: return
        viewModelScope.launch { papers.tryOpen(r, SOURCE_MANUAL) }
    }

    fun selectChartTf(tf: String) = repo.setChartTf(tf)

    fun onChartVisibleBarsChanged(n: Int) {
        if (n != settings.value.chartVisibleBars) updateSettings { it.copy(chartVisibleBars = n) }
    }

    fun onChartOverlaysChanged(packed: Int) {
        if (packed != settings.value.chartOverlays) updateSettings { it.copy(chartOverlays = packed) }
    }

    fun selectTab(i: Int) {
        savedState[KEY_TAB] = i
    }

    fun addAlarm(raw: String, kind: AlarmKind, op: AlarmOp, threshold: Double, label: String = "") {
        viewModelScope.launch { alarmsBook.add(raw, kind, op, threshold, label) }
    }

    fun setAlarmEnabled(id: Long, on: Boolean) {
        viewModelScope.launch { alarmsBook.setEnabled(id, on) }
    }

    fun deleteAlarm(id: Long) {
        viewModelScope.launch { alarmsBook.delete(id) }
    }

    fun cycleWatch(delta: Int) {
        val next = WatchCycle.pick(watchlist.value.map { it.symbol }, live.value.symbol, delta) ?: return
        submit(next)
    }

    override fun onCleared() {
        repo.stop()
        super.onCleared()
    }

    private suspend fun MarketDiscovery.runCaught(label: String) {
        runCatching { discover() }.onFailure {
            _events.send(UiEvent.Error("$label başarısız: ${it.message ?: "bilinmeyen hata"}"))
        }
    }

    private suspend fun <T> T.runCaught(label: String, block: suspend T.() -> Unit) {
        runCatching { block() }.onFailure {
            _events.send(UiEvent.Error("$label başarısız: ${it.message ?: "bilinmeyen hata"}"))
        }
    }

    companion object {
        private const val KEY_TAB = "tab"
        private const val TICK_MS = 1_000L
        private const val SUB_TIMEOUT_MS = 5_000L
        private const val OUTCOME_HISTORY = 80
        private const val SOURCE_AUTO = "auto"
        private const val SOURCE_MANUAL = "manual"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as IntelApp
                val db = AppDb.get(app)
                AppViewModel(
                    app = app,
                    savedState = createSavedStateHandle(),
                    db = db,
                    settingsStore = SettingsStore(app),
                    scanner = WatchlistScanner(app.restClient, db),
                    discovery = MarketDiscovery(app.restClient, db),
                    tracker = OutcomeTracker(db),
                    papers = PaperBook(db),
                    alarmsBook = AlarmBook(db),
                    repoFactory = { scope -> MarketRepository(app.wsClient, app.restClient, scope) },
                )
            }
        }
    }
}
