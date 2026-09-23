package com.soluciona.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest
import java.util.UUID

class LocalRepository(context: Context) {
    private val prefs = context.getSharedPreferences("soluciona_store", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_REQUESTS = "requests"
        private const val KEY_MESSAGES = "messages"
        private const val KEY_SESSION = "session"
        private const val KEY_SEEDED = "seeded"
    }

    init { seedIfNeeded() }

    fun hashPassword(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private inline fun <reified T> readList(key: String): MutableList<T> {
        val json = prefs.getString(key, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<T>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun <T> writeList(key: String, value: List<T>) {
        prefs.edit().putString(key, gson.toJson(value)).apply()
    }

    fun accounts(): MutableList<UserAccount> = readList(KEY_ACCOUNTS)
    fun providers(): MutableList<ProviderProfile> = readList(KEY_PROVIDERS)
    fun requests(): MutableList<ServiceRequest> = readList(KEY_REQUESTS)
    fun messages(): MutableList<ChatMessage> = readList(KEY_MESSAGES)

    fun currentUser(): UserAccount? {
        val id = prefs.getString(KEY_SESSION, null) ?: return null
        return accounts().firstOrNull { it.id == id }
    }

    fun register(
        name: String,
        email: String,
        password: String,
        phone: String,
        zone: String,
        role: UserRole,
        services: List<String> = emptyList(),
        license: String = "",
        baseVisit: Int = 30000
    ): Result<UserAccount> {
        val cleanEmail = email.trim().lowercase()
        if (name.isBlank() || cleanEmail.isBlank() || password.length < 6) {
            return Result.failure(IllegalArgumentException("Completá nombre, email y una contraseña de al menos 6 caracteres."))
        }
        val accounts = accounts()
        if (accounts.any { it.email.equals(cleanEmail, true) }) {
            return Result.failure(IllegalArgumentException("Ese email ya está registrado."))
        }
        val user = UserAccount(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            email = cleanEmail,
            passwordHash = hashPassword(password),
            phone = phone.trim(),
            role = role,
            zone = zone
        )
        accounts += user
        writeList(KEY_ACCOUNTS, accounts)
        if (role == UserRole.PRESTADOR) {
            val providers = providers()
            providers += ProviderProfile(
                userId = user.id,
                name = user.name,
                phone = user.phone,
                services = services.ifEmpty { listOf("Electricidad") },
                zone = zone,
                license = license.trim(),
                bio = "Profesional de Soluciona disponible para trabajos en la zona.",
                baseVisit = baseVisit.coerceAtLeast(1000),
                verified = false,
                rating = 5.0,
                jobs = 0,
                distanceKm = 1.8
            )
            writeList(KEY_PROVIDERS, providers)
        }
        prefs.edit().putString(KEY_SESSION, user.id).apply()
        return Result.success(user)
    }

    fun login(email: String, password: String): Result<UserAccount> {
        val user = accounts().firstOrNull {
            it.email.equals(email.trim(), true) && it.passwordHash == hashPassword(password)
        } ?: return Result.failure(IllegalArgumentException("Email o contraseña incorrectos."))
        prefs.edit().putString(KEY_SESSION, user.id).apply()
        return Result.success(user)
    }

    fun logout() { prefs.edit().remove(KEY_SESSION).apply() }

    fun updateProvider(profile: ProviderProfile) {
        val list = providers()
        val index = list.indexOfFirst { it.userId == profile.userId }
        if (index >= 0) list[index] = profile else list += profile
        writeList(KEY_PROVIDERS, list)
    }

    fun createRequest(client: UserAccount, provider: ProviderProfile, draft: RequestDraft): ServiceRequest {
        val request = ServiceRequest(
            id = UUID.randomUUID().toString(),
            clientId = client.id,
            clientName = client.name,
            providerId = provider.userId,
            providerName = provider.name,
            category = draft.category,
            detail = draft.detail.ifBlank { "Sin detalle adicional" },
            zone = draft.zone,
            status = RequestStatus.SOLICITADO,
            visitPrice = provider.baseVisit,
            createdAt = System.currentTimeMillis()
        )
        val list = requests()
        list.add(0, request)
        writeList(KEY_REQUESTS, list)
        return request
    }

    fun updateRequest(updated: ServiceRequest) {
        val list = requests()
        val index = list.indexOfFirst { it.id == updated.id }
        if (index >= 0) list[index] = updated
        writeList(KEY_REQUESTS, list)
    }

    fun sendMessage(requestId: String, sender: UserAccount, text: String) {
        if (text.isBlank()) return
        val list = messages()
        list += ChatMessage(
            id = UUID.randomUUID().toString(),
            requestId = requestId,
            senderId = sender.id,
            senderName = sender.name,
            text = text.trim(),
            createdAt = System.currentTimeMillis()
        )
        writeList(KEY_MESSAGES, list)
    }

    private fun seedIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val demoPass = hashPassword("demo1234")
        val accounts = mutableListOf(
            UserAccount("client-demo", "Cliente Demo", "cliente@soluciona.app", demoPass, "3415550101", UserRole.CLIENTE, "Carcarañá"),
            UserAccount("pro-martin", "Martín López", "martin@soluciona.app", demoPass, "3415550202", UserRole.PRESTADOR, "Carcarañá")
        )
        val providers = mutableListOf(
            ProviderProfile("pro-martin", "Martín López", "3415550202", listOf("Electricidad", "Climatización"), "Carcarañá", "MAT-ELEC-2048", "Electricista matriculado. Urgencias, instalaciones y mantenimiento.", 35000, true, 4.9, 126, 3.2),
            ProviderProfile("seed-carla", "Carla Fernández", "3415550303", listOf("Electricidad", "Construcción"), "Carcarañá", "MAT-ELEC-1982", "Instalaciones eléctricas residenciales y comerciales.", 38000, true, 4.8, 89, 5.1),
            ProviderProfile("seed-diego", "Diego Acosta", "3415550404", listOf("Electricidad", "Plomería", "Cerrajería"), "Carcarañá", "MAT-SRV-3150", "Reparaciones generales y asistencia a domicilio.", 32000, true, 4.7, 74, 2.4),
            ProviderProfile("seed-lucia", "Lucía Benítez", "3415550505", listOf("Plomería", "Gas"), "Rosario", "GAS-2-7741", "Gasista matriculada y plomería general.", 42000, true, 4.9, 141, 9.7),
            ProviderProfile("seed-nicolas", "Nicolás Romero", "3415550606", listOf("Climatización", "Electricidad"), "Roldán", "REF-8821", "Aire acondicionado, refrigeración y mantenimiento.", 39000, true, 4.8, 103, 7.3)
        )
        writeList(KEY_ACCOUNTS, accounts)
        writeList(KEY_PROVIDERS, providers)
        writeList<ServiceRequest>(KEY_REQUESTS, emptyList())
        writeList<ChatMessage>(KEY_MESSAGES, emptyList())
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }
}
