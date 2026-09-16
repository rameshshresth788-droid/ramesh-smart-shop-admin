package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Product(
    val id: Int,
    val product_name: String,
    val category: String,
    val product_type: String? = null,
    val description: String?,
    val old_price: Double?,
    val current_price: Double,
    val image_url: String?,
    val cloudinary_public_id: String? = null,
    val status: String = "ACTIVE"
)

@JsonClass(generateAdapter = true)
data class RequestItem(
    val id: Int = 0,
    val product_id: Int?,
    val product_name: String,
    val image_url: String? = null,
    val quantity: Int,
    val unit_price: Double,
    val subtotal: Double
)

@JsonClass(generateAdapter = true)
data class PurchaseRequest(
    val id: Int,
    val customer_name: String,
    val gender: String?,
    val total_amount: Double,
    val status: String,
    val created_at: String,
    val items: List<RequestItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Purchase(
    val id: Int,
    val customer_name: String,
    val gender: String?,
    val total_amount: Double,
    val completed_at: String,
    val items: List<RequestItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)

@JsonClass(generateAdapter = true)
data class DashboardData(
    val pending_requests: Int,
    val completed_today: Int,
    val total_products: Int,
    val active_products: Int = 0,
    val todays_sales: Double
)

@JsonClass(generateAdapter = true)
data class AiAnalysisResult(
    val product_name: String = "",
    val category: String = "",
    val product_type: String = "",
    val description: String = "",
    val confidence: String = "low",
    @Json(name = "ai_raw_failure") val aiRawFailure: Boolean = false
)

@JsonClass(generateAdapter = true)
data class UploadResult(
    val url: String,
    val cloudinary_public_id: String?
)

@JsonClass(generateAdapter = true)
data class LoginResult(
    val token: String,
    val expires_at: String?
)

@JsonClass(generateAdapter = true)
data class AdminProfile(
    val id: Int,
    val username: String,
    val email: String,
    val created_at: String
)
