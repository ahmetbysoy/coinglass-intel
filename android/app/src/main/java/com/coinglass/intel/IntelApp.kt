package com.coinglass.intel

import android.app.Application
import com.coinglass.intel.data.repo.MarketRepository
import okhttp3.OkHttpClient

class IntelApp : Application() {
    lateinit var wsClient: OkHttpClient
        private set
    lateinit var restClient: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()
        val (ws, rest) = MarketRepository.clients()
        wsClient = ws
        restClient = rest
    }
}
