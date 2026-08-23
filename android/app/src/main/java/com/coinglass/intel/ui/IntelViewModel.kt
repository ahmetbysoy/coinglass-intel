package com.coinglass.intel.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coinglass.intel.IntelApp
import com.coinglass.intel.data.repo.MarketRepository
import com.coinglass.intel.domain.Symbols
import com.coinglass.intel.domain.model.IntelUiState
import kotlinx.coroutines.flow.StateFlow

class IntelViewModel(app: Application) : AndroidViewModel(app) {
    private val intel = app as IntelApp
    private val repo = MarketRepository(intel.wsClient, intel.restClient, viewModelScope)

    val state: StateFlow<IntelUiState> = repo.state

    init {
        repo.watch("BTCUSDT")
    }

    fun submit(raw: String) {
        val s = Symbols.normalize(raw)
        repo.watch(s)
    }

    override fun onCleared() {
        repo.stop()
        super.onCleared()
    }
}
