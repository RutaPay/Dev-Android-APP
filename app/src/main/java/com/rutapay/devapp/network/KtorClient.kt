package com.rutapay.devapp.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val success: Boolean)

@Serializable
data class PaymentStatus(val id: String, val status: String, val amount: Double)

class KtorClient(private val baseUrl: String) {
    val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    suspend fun login(request: LoginRequest): LoginResponse {
        return try {
            client.post("$baseUrl/api/auth/login") {
                setBody(request)
                header("Content-Type", "application/json")
            }.body()
        } catch (e: Exception) {
            LoginResponse("", false)
        }
    }

    suspend fun getPaymentStatus(id: String): PaymentStatus? {
        return try {
            client.get("$baseUrl/api/payments/$id").body()
        } catch (e: Exception) {
            null
        }
    }
}
