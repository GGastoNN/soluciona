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
import com.mercadopago.sdk.android.domain.model.CountryCode
import com.mercadopago.sdk.android.initializer.MercadoPagoSDK

class CardCheckoutActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_CLIENT_TOKEN = "client_token"
        const val EXTRA_SELLER_PUBLIC_KEY = "seller_public_key"
        const val EXTRA_PAYMENT_REQUEST_ID = "payment_request_id"
        const val EXTRA_SERVICE_REQUEST_ID = "service_request_id"

        const val RESULT_STATUS = "result_status"
        const val RESULT_ORDER_STATUS = "order_status"
        const val RESULT_ORDER_ID = "result_order_id"
        const val RESULT_MESSAGE = "result_message"
        const val RESULT_ERROR_CODE = "result_error_code"
    }

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            launchCheckout()
        }
    }

    private fun launchCheckout() {
        if (launched) return
        launched = true

        val orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        val clientToken = intent.getStringExtra(EXTRA_CLIENT_TOKEN).orEmpty()
        val sellerPublicKey = intent.getStringExtra(EXTRA_SELLER_PUBLIC_KEY).orEmpty().trim()

        if (orderId.isBlank() || clientToken.isBlank()) {
            finishWith(
                status = "ERROR",
                orderId = orderId,
                orderStatus = "",
                message = "Falta la sesión de pago.",
                errorCode = "INVALID_SESSION",
            )
            return
        }

        if (sellerPublicKey.isBlank()) {
            finishWith(
                status = "ERROR",
                orderId = orderId,
                orderStatus = "",
                message = "Falta la Public Key del profesional. Volvé a vincular Mercado Pago.",
                errorCode = "SELLER_PUBLIC_KEY_MISSING",
            )
            return
        }

        if (!SolucionaApplication.isMercadoPagoReady()) {
            val detail = SolucionaApplication.mercadoPagoInitError()
                .ifBlank { "El SDK de Mercado Pago no quedó inicializado." }

            Log.e("SolucionaPayments", "Mercado Pago SDK no inicializado: $detail")

            finishWith(
                status = "ERROR",
                orderId = orderId,
                orderStatus = "",
                message = "SDK_NOT_INITIALIZED: $detail",
                errorCode = "SDK_NOT_INITIALIZED",
            )
            return
        }

        Log.i(
            "SolucionaPayments",
            "Abriendo CardTransaction orderId=$orderId clientTokenPresent=${clientToken.isNotBlank()}",
        )

        try {
            MercadoPagoSDK.setNewConfiguration(
                publicKey = sellerPublicKey,
                countryCode = CountryCode.ARG,
            )
            Log.i(
                "SolucionaPayments",
                "SDK configurado con Public Key del vendedor (key=${sellerPublicKey.take(8)}…).",
            )

            val checkout = MercadoPagoCheckout.Builder(
                context = this,
                checkoutType = MPCheckoutType.CardTransaction(
                    order = MPOrder(
                        orderId = orderId,
                        clientToken = clientToken,
                    ),
                ),
            )
                .setPaymentMethodConfiguration(
                    listOf(MPPaymentMethodConfig.Card())
                )
                .build()

            checkout.show { result ->
                when (result) {
                    is MercadoPagoCheckoutResult.Success -> {
                        val data = result.paymentData

                        Log.i(
                            "SolucionaPayments",
                            "MP checkout success orderId=${data.orderId} status=${data.orderStatus}",
                        )

                        finishWith(
                            status = "SUCCESS",
                            orderId = data.orderId,
                            orderStatus = data.orderStatus,
                            message = "",
                            errorCode = "",
                        )
                    }

                    is MercadoPagoCheckoutResult.Error -> {
                        val error = result.error

                        val code = error.errorCode.toString()
                        val message = error.errorMessage
                            .takeIf { it.isNotBlank() }
                            ?: "No se pudo procesar la tarjeta."

                        val localized = error.errorLocalized?.toString()
                            ?.takeIf { it.isNotBlank() }
                            ?: "sin detalle"

                        val cause = error.errorCause?.toString()
                            ?.takeIf { it.isNotBlank() }
                            ?: "sin detalle"

                        val detail = buildString {
                            append(code)
                            append(": ")
                            append(message)
                            append("\nEtapa: ")
                            append(localized)
                            append("\nCausa: ")
                            append(cause)
                        }

                        Log.e(
                            "SolucionaPayments",
                            "MP checkout error " +
                                "orderId=$orderId " +
                                "code=$code " +
                                "message=$message " +
                                "localized=$localized " +
                                "cause=$cause",
                            error,
                        )

                        finishWith(
                            status = "ERROR",
                            orderId = orderId,
                            orderStatus = "",
                            message = detail,
                            errorCode = code,
                        )
                    }

                    is MercadoPagoCheckoutResult.UserCancelled -> {
                        Log.i(
                            "SolucionaPayments",
                            "Checkout cancelado por el usuario: ${result.cancelledData}",
                        )

                        finishWith(
                            status = "CANCELLED",
                            orderId = orderId,
                            orderStatus = "",
                            message = "Pago cancelado.",
                            errorCode = "USER_CANCELLED",
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            val detail = t.message
                ?.takeIf { it.isNotBlank() }
                ?: t::class.java.simpleName

            Log.e(
                "SolucionaPayments",
                "No se pudo abrir Mercado Pago checkout: $detail",
                t,
            )

            finishWith(
                status = "ERROR",
                orderId = orderId,
                orderStatus = "",
                message = "INTEGRATION_ERROR: $detail",
                errorCode = "INTEGRATION_ERROR",
            )
        }
    }

    private fun finishWith(
        status: String,
        orderId: String,
        orderStatus: String,
        message: String,
        errorCode: String,
    ) {
        val data = Intent().apply {
            putExtra(RESULT_STATUS, status)
            putExtra(RESULT_ORDER_ID, orderId)
            putExtra(RESULT_ORDER_STATUS, orderStatus)
            putExtra(RESULT_MESSAGE, message)
            putExtra(RESULT_ERROR_CODE, errorCode)
            putExtra(
                EXTRA_PAYMENT_REQUEST_ID,
                intent.getStringExtra(EXTRA_PAYMENT_REQUEST_ID),
            )
            putExtra(
                EXTRA_SERVICE_REQUEST_ID,
                intent.getStringExtra(EXTRA_SERVICE_REQUEST_ID),
            )
        }

        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
