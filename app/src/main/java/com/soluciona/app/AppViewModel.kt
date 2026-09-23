package com.soluciona.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = LocalRepository(application)

    var currentUser by mutableStateOf(repo.currentUser())
        private set
    var providers by mutableStateOf(repo.providers().toList())
        private set
    var requests by mutableStateOf(repo.requests().toList())
        private set
    var messages by mutableStateOf(repo.messages().toList())
        private set
    var draft by mutableStateOf(RequestDraft())
    var selectedProviderId by mutableStateOf<String?>(null)
    var lastRequestId by mutableStateOf<String?>(null)
    var authError by mutableStateOf<String?>(null)
        private set

    fun clearAuthError() { authError = null }

    fun login(email: String, password: String): Boolean {
        val result = repo.login(email, password)
        currentUser = result.getOrNull()
        authError = result.exceptionOrNull()?.message
        refresh()
        return result.isSuccess
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
    ): Boolean {
        val result = repo.register(name, email, password, phone, zone, role, services, license, baseVisit)
        currentUser = result.getOrNull()
        authError = result.exceptionOrNull()?.message
        refresh()
        return result.isSuccess
    }

    fun logout() {
        repo.logout()
        currentUser = null
        draft = RequestDraft()
        selectedProviderId = null
    }

    fun refresh() {
        providers = repo.providers().toList()
        requests = repo.requests().sortedByDescending { it.createdAt }
        messages = repo.messages().sortedBy { it.createdAt }
        currentUser = repo.currentUser()
    }

    fun providersForDraft(): List<ProviderProfile> = providers
        .filter { draft.category in it.services }
        .sortedWith(compareBy<ProviderProfile> { if (it.zone == draft.zone) 0 else 1 }.thenByDescending { it.rating })

    fun selectProvider(providerId: String) { selectedProviderId = providerId }
    fun selectedProvider(): ProviderProfile? = providers.firstOrNull { it.userId == selectedProviderId }

    fun createRequest(): ServiceRequest? {
        val user = currentUser ?: return null
        val provider = selectedProvider() ?: return null
        val request = repo.createRequest(user, provider, draft)
        lastRequestId = request.id
        refresh()
        return request
    }

    fun requestById(id: String?): ServiceRequest? = requests.firstOrNull { it.id == id }

    fun myRequests(): List<ServiceRequest> {
        val user = currentUser ?: return emptyList()
        return if (user.role == UserRole.CLIENTE) requests.filter { it.clientId == user.id }
        else requests.filter { it.providerId == user.id }
    }

    fun setRequestStatus(id: String, status: RequestStatus) {
        val request = requestById(id) ?: return
        repo.updateRequest(request.copy(status = status))
        refresh()
    }

    fun rateRequest(id: String, stars: Int) {
        val request = requestById(id) ?: return
        repo.updateRequest(request.copy(rating = stars.coerceIn(1, 5)))
        refresh()
    }

    fun sendMessage(requestId: String, text: String) {
        val user = currentUser ?: return
        repo.sendMessage(requestId, user, text)
        refresh()
    }

    fun messagesFor(requestId: String): List<ChatMessage> = messages.filter { it.requestId == requestId }

    fun myProviderProfile(): ProviderProfile? {
        val id = currentUser?.id ?: return null
        return providers.firstOrNull { it.userId == id }
    }

    fun updateMyProviderProfile(services: List<String>, zone: String, license: String, bio: String, baseVisit: Int) {
        val old = myProviderProfile() ?: return
        repo.updateProvider(old.copy(services = services, zone = zone, license = license, bio = bio, baseVisit = baseVisit))
        refresh()
    }
}
