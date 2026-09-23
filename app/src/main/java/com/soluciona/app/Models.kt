package com.soluciona.app

enum class UserRole { CLIENTE, PRESTADOR }
enum class RequestStatus { SOLICITADO, CONFIRMADO, EN_CAMINO, FINALIZADO, CANCELADO }

data class UserAccount(
    val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val phone: String,
    val role: UserRole,
    val zone: String
)

data class ProviderProfile(
    val userId: String,
    val name: String,
    val phone: String,
    val services: List<String>,
    val zone: String,
    val license: String,
    val bio: String,
    val baseVisit: Int,
    val verified: Boolean,
    val rating: Double,
    val jobs: Int,
    val distanceKm: Double
)

data class ServiceRequest(
    val id: String,
    val clientId: String,
    val clientName: String,
    val providerId: String,
    val providerName: String,
    val category: String,
    val detail: String,
    val zone: String,
    val status: RequestStatus,
    val visitPrice: Int,
    val createdAt: Long,
    val rating: Int? = null
)

data class ChatMessage(
    val id: String,
    val requestId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val createdAt: Long
)

data class RequestDraft(
    val category: String = "Electricidad",
    val detail: String = "",
    val zone: String = "Carcarañá"
)
