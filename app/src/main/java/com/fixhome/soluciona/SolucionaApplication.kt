package com.fixhome.soluciona

import android.app.Application
import android.util.Log
import com.mercadopago.sdk.android.domain.model.CountryCode
import com.mercadopago.sdk.android.initializer.MercadoPagoSDK

class SolucionaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val publicKey = BuildConfig.MP_PUBLIC_KEY.trim()
        if (publicKey.isBlank()) {
            Log.w("SolucionaPayments", "MP_PUBLIC_KEY no configurada; pagos con tarjeta deshabilitados.")
            return
        }
        try {
            MercadoPagoSDK.initialize(
                context = this,
                publicKey = publicKey,
                countryCode = CountryCode.ARG,
            )
        } catch (t: Throwable) {
            Log.e("SolucionaPayments", "No se pudo inicializar Mercado Pago SDK.", t)
        }
    }
}
