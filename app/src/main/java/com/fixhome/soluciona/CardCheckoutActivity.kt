package com.fixhome.soluciona

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.mercadopago.sdk.android.coremethods.domain.interactor.coreMethods
import com.mercadopago.sdk.android.coremethods.domain.model.BuyerIdentification
import com.mercadopago.sdk.android.coremethods.domain.model.ResultError
import com.mercadopago.sdk.android.coremethods.domain.utils.Result as MPResult
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.cardnumber.CardNumberTextFieldEvent
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.cardnumber.xml.CardNumberTextField
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.expirationdate.ExpirationDateTextFieldEvent
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.expirationdate.xml.ExpirationDateTextField
import com.mercadopago.sdk.android.coremethods.ui.components.textfield.securitycode.SecurityCodeTextFieldEvent
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

    @Volatile private var paymentMethodId: String = ""
    private var paymentRequestId: String = ""
    private var serviceRequestId: String = ""
    private var amountCents: Long = 0L
    private var amountFormatted: String = ""

    private var cardValid = false
    private var expirationValid = false
    private var securityValid = false
    private var loading = false

    private val bg = Color.rgb(246, 248, 252)
    private val surface = Color.WHITE
    private val textColor = Color.rgb(23, 35, 61)
    private val muted = Color.rgb(104, 120, 144)
    private val blue = Color.rgb(47, 103, 232)
    private val line = Color.rgb(225, 232, 241)
    private val danger = Color.rgb(190, 45, 45)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paymentRequestId = intent.getStringExtra(EXTRA_PAYMENT_REQUEST_ID).orEmpty()
        serviceRequestId = intent.getStringExtra(EXTRA_SERVICE_REQUEST_ID).orEmpty()
        amountCents = intent.getLongExtra(EXTRA_AMOUNT_CENTS, 0L)
        amountFormatted = intent.getStringExtra(EXTRA_AMOUNT_FORMATTED).orEmpty()

        if (paymentRequestId.isBlank() || amountCents <= 0L) {
            finishWith("ERROR", "", "", "La sesión de cobro no es válida.", "INVALID_CARD_SESSION")
            return
        }

        if (!SolucionaApplication.isMercadoPagoReady()) {
            val detail = SolucionaApplication.mercadoPagoInitError()
                .ifBlank { "El SDK de Mercado Pago no quedó inicializado." }
            finishWith("ERROR", "", "", detail, "SDK_NOT_INITIALIZED")
            return
        }

        window.statusBarColor = bg
        window.navigationBarColor = bg

        buildUi()
        configureCardFields()
        updatePayState()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(28))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(50)
        }
        topBar.addView(TextView(this).apply {
            text = "←"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(dp(2), 0, dp(12), 0)
            setOnClickListener {
                finishWith("CANCELLED", "", "", "Pago cancelado.", "USER_CANCELLED")
            }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))

        topBar.addView(TextView(this).apply {
            text = "Soluciona."
            textSize = 21f
            setTextColor(textColor)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = -0.015f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(topBar)

        content.addView(TextView(this).apply {
            text = "Pago con tarjeta"
            textSize = 25f
            setTextColor(textColor)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(3))
        })

        content.addView(TextView(this).apply {
            text = "Completá los datos para finalizar el servicio."
            textSize = 13.5f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(14))
        })

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(surface, line, 1, 16)
            setPadding(dp(15), dp(13), dp(15), dp(13))
        }
        summary.addView(TextView(this).apply {
            text = "TOTAL A PAGAR"
            textSize = 10.5f
            setTextColor(muted)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = .08f
        })
        summary.addView(TextView(this).apply {
            text = if (amountFormatted.isNotBlank()) amountFormatted else formatAmount(amountCents)
            textSize = 24f
            setTextColor(textColor)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, 0)
        })
        content.addView(summary, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT))

        content.addView(sectionTitle("Datos de la tarjeta"))

        val cardPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(surface, line, 1, 16)
            setPadding(dp(13), dp(12), dp(13), dp(13))
        }

        cardPanel.addView(label("Número de tarjeta"))
        cardNumber = CardNumberTextField(this).apply {
            background = roundedDrawable(Color.rgb(250, 251, 253), line, 1, 12)
            setPadding(dp(10), 0, dp(10), 0)
        }
        cardPanel.addView(cardNumber, fullWidth(dp(50)).apply { topMargin = dp(5) })

        val split = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Vencimiento"))
        }
        expiration = ExpirationDateTextField(this).apply {
            background = roundedDrawable(Color.rgb(250, 251, 253), line, 1, 12)
            setPadding(dp(10), 0, dp(10), 0)
        }
        left.addView(expiration, fullWidth(dp(50)).apply { topMargin = dp(5) })

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, 0, 0)
            addView(label("Código de seguridad"))
        }
        security = SecurityCodeTextField(this).apply {
            background = roundedDrawable(Color.rgb(250, 251, 253), line, 1, 12)
            setPadding(dp(10), 0, dp(10), 0)
        }
        right.addView(security, fullWidth(dp(50)).apply { topMargin = dp(5) })

        split.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        split.addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        cardPanel.addView(split, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(11) })

        cardPanel.addView(label("Nombre del titular").apply { setPadding(0, dp(11), 0, 0) })
        holderName = standardEditText(
            hint = "Como figura en la tarjeta",
            input = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
        )
        cardPanel.addView(holderName, fullWidth(dp(50)).apply { topMargin = dp(5) })

        cardPanel.addView(label("DNI").apply { setPadding(0, dp(11), 0, 0) })
        documentNumber = standardEditText(
            hint = "Número de documento",
            input = InputType.TYPE_CLASS_NUMBER,
        )
        cardPanel.addView(documentNumber, fullWidth(dp(50)).apply { topMargin = dp(5) })

        content.addView(cardPanel, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT))

        content.addView(TextView(this).apply {
            text = "🔒  Pago seguro procesado por Mercado Pago"
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(2), dp(10), dp(2), 0)
        })

        errorText = TextView(this).apply {
            visibility = View.GONE
            textSize = 12.5f
            setTextColor(danger)
            background = roundedDrawable(Color.rgb(255, 246, 246), Color.rgb(244, 211, 211), 1, 11)
            setPadding(dp(11), dp(9), dp(11), dp(9))
        }
        content.addView(errorText, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        progress = ProgressBar(this).apply {
            visibility = View.GONE
            indeterminateTintList = ColorStateList.valueOf(blue)
        }
        content.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(12)
        })

        payButton = Button(this).apply {
            text = "Pagar ${if (amountFormatted.isNotBlank()) amountFormatted else formatAmount(amountCents)}"
            textSize = 14.5f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
            background = roundedDrawable(blue, blue, 0, 12)
            elevation = dp(2).toFloat()
            setOnClickListener { tokenizeAndPay() }
        }
        content.addView(payButton, fullWidth(dp(50)).apply { topMargin = dp(14) })

        val cancel = TextView(this).apply {
            text = "Cancelar"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(blue)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedDrawable(Color.TRANSPARENT, line, 1, 12)
            setOnClickListener {
                finishWith("CANCELLED", "", "", "Pago cancelado.", "USER_CANCELLED")
            }
        }
        content.addView(cancel, fullWidth(dp(46)).apply { topMargin = dp(8) })

        content.addView(Space(this), fullWidth(dp(12)))

        holderName.addSimpleWatcher { updatePayState() }
        documentNumber.addSimpleWatcher { updatePayState() }

        scroll.addView(content)
        setContentView(scroll)
    }

    private fun configureCardFields() {
        cardNumber.onEvent = { event ->
            when (event) {
                is CardNumberTextFieldEvent.OnBinChanged -> {
                    val bin = event.cardBin.orEmpty()
                    if (bin.length >= 6) resolvePaymentMethod(bin) else {
                        paymentMethodId = ""
                        updatePayState()
                    }
                }
                is CardNumberTextFieldEvent.IsValid -> {
                    cardValid = event.isValid
                    updatePayState()
                }
                is CardNumberTextFieldEvent.OnLastFourDigitsFilled -> {
                    cardValid = true
                    updatePayState()
                }
            }
        }

        expiration.onEvent = { event ->
            when (event) {
                is ExpirationDateTextFieldEvent.IsValid -> {
                    expirationValid = event.isValid
                    updatePayState()
                }
                is ExpirationDateTextFieldEvent.OnInputFilled -> {
                    if (event.isFilled) expirationValid = true
                    updatePayState()
                }
            }
        }

        security.onEvent = { event ->
            when (event) {
                is SecurityCodeTextFieldEvent.IsValid -> {
                    securityValid = event.isValid
                    updatePayState()
                }
                is SecurityCodeTextFieldEvent.OnInputFilled -> {
                    if (event.isFilled) securityValid = true
                    updatePayState()
                }
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
            updatePayState()
        }
    }

    private fun updatePayState() {
        if (!::payButton.isInitialized) return
        val holderOk = ::holderName.isInitialized && holderName.text?.toString()?.trim()?.isNotEmpty() == true
        val dniOk = ::documentNumber.isInitialized && documentNumber.text?.toString()?.trim()?.length?.let { it >= 7 } == true
        val ready = !loading && cardValid && expirationValid && securityValid &&
            holderOk && dniOk
        payButton.isEnabled = ready
        payButton.alpha = if (ready) 1f else .46f
    }

    private fun tokenizeAndPay() {
        val name = holderName.text?.toString()?.trim().orEmpty()
        val document = documentNumber.text?.toString()?.trim().orEmpty()

        if (!cardValid || !expirationValid || !securityValid) {
            showError("Revisá los datos de la tarjeta.")
            return
        }
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
                                else -> showError(
                                    response.message.ifBlank { "Mercado Pago rechazó el pago." }
                                )
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

    private fun postCardPayment(
        firebaseToken: String,
        cardToken: String,
        methodId: String,
    ): ApiPaymentResponse {
        var connection: HttpURLConnection? = null
        return try {
            val base = BuildConfig.MARKETPLACE_API_URL.replace(Regex("/+$"), "")
            val encoded = java.net.URLEncoder.encode(paymentRequestId, "UTF-8")
            val url = URL("$base/v1/payment-requests/$encoded/card-pay")
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
            val stream: InputStream? =
                if (code in 200..299) connection.inputStream else connection.errorStream
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

    private fun setLoading(value: Boolean) {
        loading = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        if (value) errorText.visibility = View.GONE
        payButton.text = if (value) "Procesando…" else
            "Pagar ${if (amountFormatted.isNotBlank()) amountFormatted else formatAmount(amountCents)}"
        updatePayState()
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

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(textColor)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(1), dp(18), 0, dp(8))
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(muted)
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun standardEditText(hint: String, input: Int) = EditText(this).apply {
        this.hint = hint
        inputType = input
        setSingleLine(true)
        textSize = 14f
        setTextColor(textColor)
        setHintTextColor(Color.rgb(151, 164, 183))
        background = roundedDrawable(Color.rgb(250, 251, 253), line, 1, 12)
        setPadding(dp(12), 0, dp(12), 0)
    }

    private fun EditText.addSimpleWatcher(after: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, afterCount: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = after()
        })
    }

    private fun roundedDrawable(
        fill: Int,
        stroke: Int,
        strokeDp: Int,
        radiusDp: Int,
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

    private fun fullWidth(height: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        height,
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun formatAmount(cents: Long): String =
        "$ " + String.format("%,.2f", cents / 100.0)

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
