package com.fixhome.soluciona

import android.app.Application
import android.util.Log
import com.mercadopago.sdk.android.domain.model.CountryCode
import com.mercadopago.sdk.android.initializer.MercadoPagoSDK

class SolucionaApplication : Application() {
    companion object {
        @Volatile
        private var mercadoPagoReady = false

        @Volatile
        private var mercadoPagoInitError = ""

        @JvmStatic
        fun isMercadoPagoReady(): Boolean = mercadoPagoReady

        @JvmStatic
        fun mercadoPagoInitError(): String = mercadoPagoInitError
    }

    override fun onCreate() {
        super.onCreate()

        mercadoPagoReady = false
        mercadoPagoInitError = ""

        val publicKey = BuildConfig.MP_PUBLIC_KEY.trim()
        if (publicKey.isBlank()) {
            mercadoPagoInitError = "MP_PUBLIC_KEY no está configurada en esta compilación."
            Log.e("SolucionaPayments", mercadoPagoInitError)
            return
        }

        try {
            MercadoPagoSDK.initialize(
                context = this,
                publicKey = publicKey,
                countryCode = CountryCode.ARG,
            )

            // Force the singleton lookup here. If initialization was rejected we want
            // to know before the user tries to open Card Payment.
            MercadoPagoSDK.getInstance()
            mercadoPagoReady = true
            Log.i(
                "SolucionaPayments",
                "Mercado Pago SDK inicializado correctamente (key=${publicKey.take(8)}…).",
            )
        } catch (t: Throwable) {
            mercadoPagoInitError = buildString {
                append("No se pudo inicializar Mercado Pago")
                val detail = t.message?.trim().orEmpty()
                if (detail.isNotBlank()) append(": ").append(detail)
            }
            Log.e("SolucionaPayments", mercadoPagoInitError, t)
        }
    }
}
