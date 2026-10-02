package com.fixhome.soluciona

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.mercadopago.sdk.android.checkout.core.MercadoPagoCheckout
import com.mercadopago.sdk.android.checkout.core.model.MPCheckoutType
import com.mercadopago.sdk.android.checkout.core.model.MPOrder
import com.mercadopago.sdk.android.checkout.core.model.MPPaymentMethodConfig
import com.mercadopago.sdk.android.checkout.domain.callback.MercadoPagoCheckoutResult

class CardCheckoutActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_CLIENT_TOKEN = "client_token"
        const val EXTRA_PAYMENT_REQUEST_ID = "payment_request_id"
        const val EXTRA_SERVICE_REQUEST_ID = "service_request_id"

        const val RESULT_STATUS = "result_status"
        const val RESULT_ORDER_STATUS = "order_status"
        const val RESULT_ORDER_ID = "result_order_id"
        const val RESULT_MESSAGE = "result_message"
    }

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchCheckout()
    }

    private fun launchCheckout() {
        if (launched) return
        launched = true

        val orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        val clientToken = intent.getStringExtra(EXTRA_CLIENT_TOKEN).orEmpty()
        if (orderId.isBlank() || clientToken.isBlank()) {
            finishWith("ERROR", orderId, "", "Falta la sesión de pago.")
            return
        }

        val checkout = MercadoPagoCheckout.Builder(
            context = this,
            checkoutType = MPCheckoutType.CardTransaction(
                order = MPOrder(
                    orderId = orderId,
                    clientToken = clientToken,
                ),
            ),
        ).setPaymentMethodConfiguration(listOf(MPPaymentMethodConfig.Card()))
            .build()

        checkout.show { result ->
            when (result) {
                is MercadoPagoCheckoutResult.Success -> {
                    val data = result.paymentData
                    finishWith(
                        status = "SUCCESS",
                        orderId = data.orderId,
                        orderStatus = data.orderStatus,
                        message = "",
                    )
                }

                is MercadoPagoCheckoutResult.Error -> {
                    // Keep gateway details in Logcat for diagnosis, but do not expose
                    // them to the WebView/UI. Mercado Pago errors can otherwise look
                    // like the checkout simply opened and immediately closed.
                    Log.e("SolucionaPayments", "Mercado Pago checkout error: $result")
                    finishWith(
                        status = "ERROR",
                        orderId = orderId,
                        orderStatus = "",
                        message = "No se pudo procesar la tarjeta. Revisá los datos o probá otro medio.",
                    )
                }

                is MercadoPagoCheckoutResult.UserCancelled -> {
                    finishWith(
                        status = "CANCELLED",
                        orderId = orderId,
                        orderStatus = "",
                        message = "Pago cancelado.",
                    )
                }
            }
        }
    }

    private fun finishWith(
        status: String,
        orderId: String,
        orderStatus: String,
        message: String,
    ) {
        val data = Intent().apply {
            putExtra(RESULT_STATUS, status)
            putExtra(RESULT_ORDER_ID, orderId)
            putExtra(RESULT_ORDER_STATUS, orderStatus)
            putExtra(RESULT_MESSAGE, message)
            putExtra(EXTRA_PAYMENT_REQUEST_ID, intent.getStringExtra(EXTRA_PAYMENT_REQUEST_ID))
            putExtra(EXTRA_SERVICE_REQUEST_ID, intent.getStringExtra(EXTRA_SERVICE_REQUEST_ID))
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
