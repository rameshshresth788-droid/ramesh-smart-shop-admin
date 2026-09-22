package com.example.api

import com.example.data.AdminProfile
import com.example.data.AiAnalysisResult
import com.example.data.ApiResponse
import com.example.data.DashboardData
import com.example.data.Product
import com.example.data.Purchase
import com.example.data.PurchaseRequest
import com.example.data.UploadResult
import com.example.data.LoginResult
import com.example.data.StaffActionRequest
import com.example.data.StaffAdmin
import com.example.data.StaffEditRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query

interface BackendApi {
    // ---- Admin auth ----
    @POST("api/admin/login.php")
    suspend fun login(@Body body: Map<String, String>): ApiResponse<LoginResult>

    @POST("api/admin/logout.php")
    suspend fun logout(): ApiResponse<Any>

    @GET("api/admin/profile.php")
    suspend fun getProfile(): ApiResponse<AdminProfile>

    @GET("api/admin/dashboard.php")
    suspend fun getDashboard(): ApiResponse<DashboardData>

    // ---- Products ----
    @GET("api/products/index.php")
    suspend fun getProducts(@Query("status") status: String? = null): ApiResponse<List<Product>>

    @GET("api/products/index.php")
    suspend fun getProduct(@Query("id") id: Int): ApiResponse<Product>

    @POST("api/products/index.php")
    suspend fun addProduct(@Body product: Map<String, @JvmSuppressWildcards Any?>): ApiResponse<Map<String, Int>>

    @PUT("api/products/index.php")
    suspend fun updateProduct(@Query("id") id: Int, @Body product: Map<String, @JvmSuppressWildcards Any?>): ApiResponse<Any>

    @DELETE("api/products/index.php")
    suspend fun deleteProduct(@Query("id") id: Int): ApiResponse<Any>

    // ---- Image upload (goes to Cloudinary if configured, else local storage) ----
    @Multipart
    @POST("api/upload/index.php")
    suspend fun uploadImage(@Part image: MultipartBody.Part): ApiResponse<UploadResult>

    // ---- AI analysis (server holds the AI key - app never sees it) ----
    @POST("api/ai/analyze-product.php")
    suspend fun analyzeProduct(@Body body: Map<String, String>): ApiResponse<AiAnalysisResult>

    // ---- User requests (admin side) ----
    @GET("api/admin/requests/index.php")
    suspend fun getRequests(@Query("status") status: String = "PENDING"): ApiResponse<List<PurchaseRequest>>

    @GET("api/admin/requests/index.php")
    suspend fun getRequestDetail(@Query("id") id: Int): ApiResponse<PurchaseRequest>

    @POST("api/admin/requests/complete.php")
    suspend fun completeRequest(@Query("id") id: Int): ApiResponse<Map<String, Int>>

    @POST("api/admin/requests/cancel.php")
    suspend fun cancelRequest(@Query("id") id: Int): ApiResponse<Any>

    // ---- Purchase history (24h) ----
    @GET("api/purchases/index.php")
    suspend fun getPurchases(): ApiResponse<List<Purchase>>

    @DELETE("api/purchases/index.php")
    suspend fun deletePurchase(@Query("id") id: Int): ApiResponse<Any>

    // ---- Staff registration (no auth token yet - public) ----
    @Multipart
    @POST("api/admin/staff/register.php")
    suspend fun registerStaff(
        @Part("name") name: okhttp3.RequestBody,
        @Part("age") age: okhttp3.RequestBody,
        @Part("gender") gender: okhttp3.RequestBody,
        @Part("username") username: okhttp3.RequestBody,
        @Part("phone") phone: okhttp3.RequestBody,
        @Part("password") password: okhttp3.RequestBody,
        @Part("gemini_api_key") geminiApiKey: okhttp3.RequestBody,
        @Part photo: MultipartBody.Part?
    ): ApiResponse<Map<String, @JvmSuppressWildcards Any?>>

    // ---- Staff management (Head Admin only) ----
    @GET("api/admin/staff/index.php")
    suspend fun getStaff(): ApiResponse<List<StaffAdmin>>

    @GET("api/admin/staff/index.php")
    suspend fun getStaffDetail(@Query("id") id: Int): ApiResponse<StaffAdmin>

    @POST("api/admin/staff/index.php")
    suspend fun staffAction(@Body body: StaffActionRequest): ApiResponse<StaffAdmin>

    @PUT("api/admin/staff/index.php")
    suspend fun updateStaff(@Query("id") id: Int, @Body body: StaffEditRequest): ApiResponse<StaffAdmin>

    @DELETE("api/admin/staff/index.php")
    suspend fun deleteStaff(@Query("id") id: Int): ApiResponse<Any>

    // ---- Settings ----
    @GET("api/settings/index.php")
    suspend fun getSettings(): ApiResponse<Map<String, Any>>

    @POST("api/settings/index.php")
    suspend fun saveSettings(@Body settings: Map<String, String>): ApiResponse<Map<String, Any>>
}
