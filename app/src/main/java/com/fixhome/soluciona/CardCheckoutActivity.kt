package com.fixhome.soluciona

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.mercadopago.sdk.android.coremethods.domain.interactor.coreMethods
import com.mercadopago.sdk.android.coremethods.domain.model.BuyerIdentification
import com.mercadopago.sdk.android.coremethods.domain.model.ResultError
import com.mercadopago.sdk.android.coremethods.domain.utils.Result as MPResult
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.cardnumber.CardNumberTextFieldEvent
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.cardnumber.xml.CardNumberTextField
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.expirationdate.xml.ExpirationDateTextField
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.securitycode.xml.SecurityCodeTextField
import com.mercadopago.sdk.android.initializer.MercadoPagoSDK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class CardCheckoutActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PAYMENT_REQUEST_ID = "payment_request_id"
        const val EXTRA_SERVICE_REQUEST_ID = "service_request_id"
        const val EXTRA_AMOUNT_CENTS = "amount_cents"
        const val EXTRA_AMOUNT_FORMATTED = "amount_formatted"

        const val RESULT_STATUS = "result_status"
        const val RESULT_ORDER_STATUS = "order_status"
        const val RESULT_ORDER_ID = "result_order_id"
        const val RESULT_MESSAGE = "result_message"
        const val RESULT_ERROR_CODE = "result_error_code"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var cardNumber: CardNumberTextField
    private lateinit var expiration: ExpirationDateTextField
    private lateinit var security: SecurityCodeTextField
    private lateinit var holderName: EditText
    private lateinit var documentNumber: EditText
    private lateinit var payButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView

    @Volatile
    private var paymentMethodId: String = ""
    private var paymentRequestId: String = ""
    private var serviceRequestId: String = ""
    private var amountCents: Long = 0L
    private var amountFormatted: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paymentRequestId = intent.getStringExtra(EXTRA_PAYMENT_REQUEST_ID).orEmpty()
        serviceRequestId = intent.getStringExtra(EXTRA_SERVICE_REQUEST_ID).orEmpty()
        amountCents = intent.getLongExtra(EXTRA_AMOUNT_CENTS, 0L)
        amountFormatted = intent.getStringExtra(EXTRA_AMOUNT_FORMATTED).orEmpty()

        if (paymentRequestId.isBlank() || amountCents <= 0L) {
            finishWith(
                status = "ERROR",
                paymentId = "",
                paymentStatus = "",
                message = "La sesión de cobro no es válida.",
                errorCode = "INVALID_CARD_SESSION",
            )
            return
        }

        if (!SolucionaApplication.isMercadoPagoReady()) {
            val detail = SolucionaApplication.mercadoPagoInitError()
                .ifBlank { "El SDK de Mercado Pago no quedó inicializado." }
            finishWith("ERROR", "", "", detail, "SDK_NOT_INITIALIZED")
            return
        }

        buildUi()
        configureCardFields()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "Pago con tarjeta"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = if (amountFormatted.isNotBlank()) amountFormatted else formatAmount(amountCents)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(20))
        })

        root.addView(label("Número de tarjeta"))
        cardNumber = CardNumberTextField(this)
        root.addView(cardNumber, fullWidth(dp(56)))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Vencimiento"))
        }
        expiration = ExpirationDateTextField(this)
        left.addView(expiration, fullWidth(dp(56)))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(label("Código de seguridad"))
        }
        security = SecurityCodeTextField(this)
        right.addView(security, fullWidth(dp(56)))

        row.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        root.addView(label("Nombre del titular").apply { setPadding(0, dp(18), 0, dp(6)) })
        holderName = EditText(this).apply {
            hint = "Como figura en la tarjeta"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
        }
        root.addView(holderName, fullWidth(dp(56)))

        root.addView(label("DNI").apply { setPadding(0, dp(14), 0, dp(6)) })
        documentNumber = EditText(this).apply {
            hint = "Número de documento"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        root.addView(documentNumber, fullWidth(dp(56)))

        errorText = TextView(this).apply {
            visibility = View.GONE
            setPadding(0, dp(14), 0, dp(8))
        }
        root.addView(errorText, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT))

        progress = ProgressBar(this).apply {
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(12)
        })

        payButton = Button(this).apply {
            text = "Pagar ${if (amountFormatted.isNotBlank()) amountFormatted else formatAmount(amountCents)}"
            setOnClickListener { tokenizeAndPay() }
        }
        root.addView(payButton, fullWidth(dp(56)).apply { topMargin = dp(18) })

        val cancel = Button(this).apply {
            text = "Cancelar"
            setOnClickListener {
                finishWith("CANCELLED", "", "", "Pago cancelado.", "USER_CANCELLED")
            }
        }
        root.addView(cancel, fullWidth(dp(52)).apply { topMargin = dp(8) })

        setContentView(root)
    }

    private fun configureCardFields() {
        cardNumber.onEvent = { event ->
            if (event is CardNumberTextFieldEvent.OnBinChanged) {
                val bin = event.cardBin.orEmpty()
                if (bin.length >= 6) resolvePaymentMethod(bin)
                else paymentMethodId = ""
            }
        }
    }

    private fun resolvePaymentMethod(bin: String) {
        scope.launch {
            try {
                val result = MercadoPagoSDK.getInstance().coreMethods.getPaymentMethods(bin)
                when (result) {
                    is MPResult.Success -> {
                        val method = result.data.firstOrNull { !it.id.isNullOrBlank() }
                        paymentMethodId = method?.id.orEmpty()
                        method?.card?.length?.max?.let { max ->
                            if (max in 8..19) cardNumber.maxLength = max
                        }
                        method?.card?.securityCode?.length?.let { size ->
                            if (size in 3..4) security.securityCodeSize = size
                        }
                    }
                    is MPResult.Error -> {
                        paymentMethodId = ""
                        Log.w("SolucionaPayments", "No se pudo resolver payment method: ${errorMessage(result.error)}")
                    }
                }
            } catch (t: Throwable) {
                paymentMethodId = ""
                Log.e("SolucionaPayments", "Error resolviendo medio de pago", t)
            }
        }
    }

    private fun tokenizeAndPay() {
        val name = holderName.text?.toString()?.trim().orEmpty()
        val document = documentNumber.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            showError("Ingresá el nombre del titular.")
            return
        }
        if (document.length < 7) {
            showError("Ingresá un DNI válido.")
            return
        }

        setLoading(true)
        scope.launch {
            try {
                val coreMethods = MercadoPagoSDK.getInstance().coreMethods
                val tokenResult = coreMethods.generateCardToken(
                    cardNumberState = cardNumber.state,
                    expirationDateState = expiration.state,
                    securityCodeState = security.state,
                    buyerIdentification = BuyerIdentification(
                        name = name,
                        number = document,
                        type = "DNI",
                    ),
                )

                when (tokenResult) {
                    is MPResult.Success -> {
                        val token = tokenResult.data.token
                        val bin = tokenResult.data.firstSixDigits.orEmpty()
                        var methodId = paymentMethodId
                        if (methodId.isBlank() && bin.length >= 6) {
                            val methods = coreMethods.getPaymentMethods(bin)
                            if (methods is MPResult.Success) {
                                methodId = methods.data.firstOrNull { !it.id.isNullOrBlank() }?.id.orEmpty()
                            }
                        }
                        if (methodId.isBlank()) {
                            setLoading(false)
                            showError("No pudimos identificar la tarjeta. Revisá los datos e intentá nuevamente.")
                            return@launch
                        }
                        submitPayment(token, methodId)
                    }
                    is MPResult.Error -> {
                        setLoading(false)
                        showError("No se pudo validar la tarjeta: ${errorMessage(tokenResult.error)}")
                    }
                }
            } catch (t: Throwable) {
                Log.e("SolucionaPayments", "Tokenización de tarjeta falló", t)
                setLoading(false)
                showError(t.message?.takeIf { it.isNotBlank() } ?: "No se pudo validar la tarjeta.")
            }
        }
    }

    private fun submitPayment(cardToken: String, methodId: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            setLoading(false)
            showError("La sesión de Soluciona venció. Volvé a ingresar.")
            return
        }

        user.getIdToken(true)
            .addOnSuccessListener { result ->
                val firebaseToken = result.token.orEmpty()
                if (firebaseToken.isBlank()) {
                    setLoading(false)
                    showError("No se pudo obtener una sesión válida.")
                    return@addOnSuccessListener
                }

                Thread {
                    val response = postCardPayment(firebaseToken, cardToken, methodId)
                    runOnUiThread {
                        setLoading(false)
                        if (response.ok) {
                            when (response.status.lowercase()) {
                                "approved" -> finishWith(
                                    "SUCCESS",
                                    response.paymentId,
                                    response.status,
                                    "",
                                    "",
                                )
                                "pending", "in_process", "in_mediation" -> finishWith(
                                    "ERROR",
                                    response.paymentId,
                                    response.status,
                                    "El pago quedó en proceso. Revisá el estado en unos instantes.",
                                    "PAYMENT_PENDING",
                                )
                                else -> showError(response.message.ifBlank { "Mercado Pago rechazó el pago." })
                            }
                        } else {
                            showError(response.message.ifBlank { "No se pudo procesar el pago." })
                        }
                    }
                }.start()
            }
            .addOnFailureListener { error ->
                setLoading(false)
                showError(error.message ?: "No se pudo validar la sesión.")
            }
    }

    private fun postCardPayment(firebaseToken: String, cardToken: String, methodId: String): ApiPaymentResponse {
        var connection: HttpURLConnection? = null
        return try {
            val base = BuildConfig.MARKETPLACE_API_URL.replace(Regex("/+$"), "")
            val url = URL("$base/v1/payment-requests/${java.net.URLEncoder.encode(paymentRequestId, "UTF-8")}/card-pay")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 35_000
            connection.setRequestProperty("Authorization", "Bearer $firebaseToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val body = JSONObject()
                .put("token", cardToken)
                .put("paymentMethodId", methodId)
                .put("installments", 1)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)

            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { out: OutputStream -> out.write(body) }

            val code = connection.responseCode
            val stream: InputStream? = if (code in 200..299) connection.inputStream else connection.errorStream
            val payloadText = readAll(stream)
            val payload = if (payloadText.isBlank()) JSONObject() else JSONObject(payloadText)
            ApiPaymentResponse(
                ok = code in 200..299,
                paymentId = payload.optString("paymentId", ""),
                status = payload.optString("status", ""),
                message = payload.optString("message", payload.optString("error", "")),
            )
        } catch (t: Throwable) {
            Log.e("SolucionaPayments", "Error llamando card-pay", t)
            ApiPaymentResponse(false, "", "", t.message ?: "Error de red.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun errorMessage(error: ResultError): String = when (error) {
        is ResultError.Request -> error.message
        is ResultError.Validation -> error.message
    }

    private fun setLoading(loading: Boolean) {
        payButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) errorText.visibility = View.GONE
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun finishWith(
        status: String,
        paymentId: String,
        paymentStatus: String,
        message: String,
        errorCode: String,
    ) {
        val data = Intent().apply {
            putExtra(RESULT_STATUS, status)
            putExtra(RESULT_ORDER_ID, paymentId)
            putExtra(RESULT_ORDER_STATUS, paymentStatus)
            putExtra(RESULT_MESSAGE, message)
            putExtra(RESULT_ERROR_CODE, errorCode)
            putExtra(EXTRA_PAYMENT_REQUEST_ID, paymentRequestId)
            putExtra(EXTRA_SERVICE_REQUEST_ID, serviceRequestId)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
    }

    private fun fullWidth(height: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        height,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatAmount(cents: Long): String = "$ " + String.format("%,.2f", cents / 100.0)

    private fun readAll(input: InputStream?): String {
        if (input == null) return ""
        return BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            buildString {
                var line: String?
                while (reader.readLine().also { line = it } != null) append(line)
            }
        }
    }

    private data class ApiPaymentResponse(
        val ok: Boolean,
        val paymentId: String,
        val status: String,
        val message: String,
    )
}
