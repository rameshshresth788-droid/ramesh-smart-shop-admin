package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiClient
import com.example.api.BackendApi
import com.example.data.*
import com.example.util.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.IOException

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

/** Draft product being built in the Add/Edit Product screen. */
data class ProductDraft(
    val id: Int = 0,
    val imageUri: String? = null,      // local preview (camera/gallery)
    val imageUrl: String? = null,       // final uploaded URL (server or Cloudinary)
    val cloudinaryPublicId: String? = null,
    val productName: String = "",
    val category: String = "",
    val productType: String = "",
    val description: String = "",
    val price: String = "",
    val status: String = "ACTIVE",
    val aiConfidence: String? = null
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)

    val apiBaseUrl = settingsManager.apiBaseUrl
    val adminToken = settingsManager.adminToken

    private val _loginState = MutableStateFlow<UiState<Boolean>>(UiState.Idle)
    val loginState: StateFlow<UiState<Boolean>> = _loginState

    private val _dashboardState = MutableStateFlow<UiState<DashboardData>>(UiState.Idle)
    val dashboardState: StateFlow<UiState<DashboardData>> = _dashboardState

    private val _productsState = MutableStateFlow<UiState<List<Product>>>(UiState.Idle)
    val productsState: StateFlow<UiState<List<Product>>> = _productsState

    private val _requestsState = MutableStateFlow<UiState<List<PurchaseRequest>>>(UiState.Idle)
    val requestsState: StateFlow<UiState<List<PurchaseRequest>>> = _requestsState

    private val _purchasesState = MutableStateFlow<UiState<List<Purchase>>>(UiState.Idle)
    val purchasesState: StateFlow<UiState<List<Purchase>>> = _purchasesState

    private val _aiAnalysisState = MutableStateFlow<UiState<AiAnalysisResult>>(UiState.Idle)
    val aiAnalysisState: StateFlow<UiState<AiAnalysisResult>> = _aiAnalysisState

    private val _saveProductState = MutableStateFlow<UiState<Boolean>>(UiState.Idle)
    val saveProductState: StateFlow<UiState<Boolean>> = _saveProductState

    private val _settingsState = MutableStateFlow<UiState<Map<String, Any>>>(UiState.Idle)
    val settingsState: StateFlow<UiState<Map<String, Any>>> = _settingsState

    private val _testConnectionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val testConnectionState: StateFlow<UiState<String>> = _testConnectionState

    private val _productDraft = MutableStateFlow(ProductDraft())
    val productDraft: StateFlow<ProductDraft> = _productDraft

    private var capturedBitmap: Bitmap? = null

    private suspend fun getApi(): BackendApi = ApiClient.getBackendApi(apiBaseUrl.first(), adminToken.first())

    private fun friendlyError(e: Exception): String = when (e) {
        is IOException -> "Can't reach the server. Check your internet connection and the API URL in Settings."
        is HttpException -> "Server error (HTTP ${e.code()}). Please try again."
        else -> e.message ?: "Something went wrong. Please try again."
    }

    // ================= AUTH =================
    fun login(username: String, pass: String) {
        if (username.isBlank() || pass.isBlank()) {
            _loginState.value = UiState.Error("Please enter username and password")
            return
        }
        viewModelScope.launch {
            _loginState.value = UiState.Loading
            try {
                val api = ApiClient.getBackendApi(apiBaseUrl.first(), "")
                val res = api.login(mapOf("username" to username, "password" to pass))
                if (res.success && res.data != null) {
                    settingsManager.saveAdminToken(res.data.token)
                    _loginState.value = UiState.Success(true)
                } else {
                    _loginState.value = UiState.Error(res.message)
                }
            } catch (e: Exception) {
                _loginState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                getApi().logout()
            } catch (e: Exception) {
                // Even if the network call fails, still clear the local session below.
            }
            settingsManager.clearSession()
            _loginState.value = UiState.Idle
            _dashboardState.value = UiState.Idle
            _productsState.value = UiState.Idle
            _requestsState.value = UiState.Idle
            _purchasesState.value = UiState.Idle
        }
    }

    // ================= DASHBOARD =================
    fun loadDashboard() {
        viewModelScope.launch {
            _dashboardState.value = UiState.Loading
            try {
                val res = getApi().getDashboard()
                if (res.success && res.data != null) _dashboardState.value = UiState.Success(res.data)
                else _dashboardState.value = UiState.Error(res.message)
            } catch (e: Exception) {
                _dashboardState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    // ================= PRODUCTS =================
    fun loadProducts() {
        viewModelScope.launch {
            _productsState.value = UiState.Loading
            try {
                val res = getApi().getProducts()
                if (res.success && res.data != null) _productsState.value = UiState.Success(res.data)
                else _productsState.value = UiState.Error(res.message)
            } catch (e: Exception) {
                _productsState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    fun toggleProductStatus(product: Product) {
        viewModelScope.launch {
            try {
                val newStatus = if (product.status == "ACTIVE") "INACTIVE" else "ACTIVE"
                getApi().updateProduct(product.id, mapOf("status" to newStatus, "current_price" to product.current_price))
                loadProducts()
            } catch (e: Exception) {
                _productsState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    fun deleteProduct(id: Int) {
        viewModelScope.launch {
            try {
                getApi().deleteProduct(id)
                loadProducts()
            } catch (e: Exception) {
                _productsState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    fun startNewProduct() {
        capturedBitmap = null
        _productDraft.value = ProductDraft()
        _aiAnalysisState.value = UiState.Idle
        _saveProductState.value = UiState.Idle
    }

    fun startEditProduct(product: Product) {
        capturedBitmap = null
        _productDraft.value = ProductDraft(
            id = product.id,
            imageUrl = product.image_url,
            cloudinaryPublicId = product.cloudinary_public_id,
            productName = product.product_name,
            category = product.category,
            productType = product.product_type ?: "",
            description = product.description ?: "",
            price = product.current_price.toString(),
            status = product.status
        )
        _aiAnalysisState.value = UiState.Idle
        _saveProductState.value = UiState.Idle
    }

    fun updateDraftField(update: (ProductDraft) -> ProductDraft) {
        _productDraft.value = update(_productDraft.value)
    }

    /** Called after camera capture or gallery pick, with the local preview URI + decoded bitmap. */
    fun onImageSelected(uri: String, bitmap: Bitmap) {
        capturedBitmap = bitmap
        _productDraft.value = _productDraft.value.copy(imageUri = uri, imageUrl = null, cloudinaryPublicId = null)
        analyzeCapturedImage(bitmap)
    }

    fun retakePhoto() {
        capturedBitmap = null
        _productDraft.value = _productDraft.value.copy(imageUri = null)
        _aiAnalysisState.value = UiState.Idle
    }

    private fun analyzeCapturedImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _aiAnalysisState.value = UiState.Loading
            try {
                val small = ImageUtils.downscale(bitmap, 1024)
                val base64 = ImageUtils.bitmapToBase64Jpeg(small, 80)
                val res = getApi().analyzeProduct(mapOf("image_base64" to base64))
                if (res.success && res.data != null) {
                    val ai = res.data
                    if (!ai.aiRawFailure) {
                        _productDraft.value = _productDraft.value.copy(
                            productName = ai.product_name.ifBlank { _productDraft.value.productName },
                            category = ai.category.ifBlank { _productDraft.value.category },
                            productType = ai.product_type.ifBlank { _productDraft.value.productType },
                            description = ai.description.ifBlank { _productDraft.value.description },
                            aiConfidence = ai.confidence
                        )
                    }
                    _aiAnalysisState.value = UiState.Success(ai)
                } else {
                    // AI failed - never block the admin, they can still type everything manually.
                    _aiAnalysisState.value = UiState.Error(res.message)
                }
            } catch (e: Exception) {
                _aiAnalysisState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    /** Uploads the image (if a new one was captured) then creates/updates the product. Admin-entered price always wins. */
    fun saveProduct() {
        val draft = _productDraft.value
        val priceValue = draft.price.toDoubleOrNull()

        if (draft.productName.isBlank() || draft.category.isBlank()) {
            _saveProductState.value = UiState.Error("Product name and category are required")
            return
        }
        if (priceValue == null || priceValue < 0) {
            _saveProductState.value = UiState.Error("Please enter a valid price")
            return
        }

        viewModelScope.launch {
            _saveProductState.value = UiState.Loading
            try {
                var imageUrl = draft.imageUrl
                var publicId = draft.cloudinaryPublicId

                val bitmap = capturedBitmap
                if (bitmap != null) {
                    val file = ImageUtils.bitmapToTempFile(getApplication(), bitmap)
                    val body = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("image", file.name, body)
                    val uploadRes = getApi().uploadImage(part)
                    if (!uploadRes.success || uploadRes.data == null) {
                        _saveProductState.value = UiState.Error(uploadRes.message.ifBlank { "Image upload failed" })
                        return@launch
                    }
                    imageUrl = uploadRes.data.url
                    publicId = uploadRes.data.cloudinary_public_id
                }

                val payload = mapOf(
                    "product_name" to draft.productName.trim(),
                    "category" to draft.category.trim(),
                    "product_type" to draft.productType.trim(),
                    "description" to draft.description.trim(),
                    "current_price" to priceValue,
                    "image_url" to imageUrl,
                    "cloudinary_public_id" to publicId,
                    "status" to draft.status
                )

                val res = if (draft.id == 0) getApi().addProduct(payload) else getApi().updateProduct(draft.id, payload)
                if (res.success) {
                    _saveProductState.value = UiState.Success(true)
                    loadProducts()
                } else {
                    _saveProductState.value = UiState.Error(res.message)
                }
            } catch (e: Exception) {
                _saveProductState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    // ================= REQUESTS =================
    fun loadRequests() {
        viewModelScope.launch {
            _requestsState.value = UiState.Loading
            try {
                val res = getApi().getRequests("PENDING")
                if (res.success && res.data != null) _requestsState.value = UiState.Success(res.data)
                else _requestsState.value = UiState.Error(res.message)
            } catch (e: Exception) {
                _requestsState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    fun completeRequest(id: Int) {
        viewModelScope.launch {
            try {
                val res = getApi().completeRequest(id)
                if (!res.success) _requestsState.value = UiState.Error(res.message)
                loadRequests()
            } catch (e: Exception) {
                _requestsState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    fun cancelRequest(id: Int) {
        viewModelScope.launch {
            try {
                val res = getApi().cancelRequest(id)
                if (!res.success) _requestsState.value = UiState.Error(res.message)
                loadRequests()
            } catch (e: Exception) {
                _requestsState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    // ================= PURCHASES (24h history) =================
    fun loadPurchases() {
        viewModelScope.launch {
            _purchasesState.value = UiState.Loading
            try {
                val res = getApi().getPurchases()
                if (res.success && res.data != null) _purchasesState.value = UiState.Success(res.data)
                else _purchasesState.value = UiState.Error(res.message)
            } catch (e: Exception) {
                _purchasesState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    fun deletePurchase(id: Int) {
        viewModelScope.launch {
            try {
                getApi().deletePurchase(id)
                loadPurchases()
            } catch (e: Exception) {
                _purchasesState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    // ================= SETTINGS =================
    fun loadSettings() {
        viewModelScope.launch {
            _settingsState.value = UiState.Loading
            try {
                val res = getApi().getSettings()
                if (res.success && res.data != null) _settingsState.value = UiState.Success(res.data)
                else _settingsState.value = UiState.Error(res.message)
            } catch (e: Exception) {
                _settingsState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    fun saveApiBaseUrl(url: String) {
        viewModelScope.launch { settingsManager.saveApiBaseUrl(url) }
    }

    /** Only non-blank secret fields are sent, so leaving them blank keeps the existing server-side value. */
    fun saveBackendSettings(fields: Map<String, String>) {
        viewModelScope.launch {
            _settingsState.value = UiState.Loading
            try {
                val res = getApi().saveSettings(fields)
                if (res.success && res.data != null) _settingsState.value = UiState.Success(res.data)
                else _settingsState.value = UiState.Error(res.message)
            } catch (e: Exception) {
                _settingsState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    fun testConnection(url: String) {
        viewModelScope.launch {
            _testConnectionState.value = UiState.Loading
            try {
                val api = ApiClient.getBackendApi(url, "")
                val res = api.getProducts()
                if (res.success) {
                    _testConnectionState.value = UiState.Success("Connected successfully!")
                } else {
                    _testConnectionState.value = UiState.Error("Server responded but with an error: ${res.message}")
                }
            } catch (e: Exception) {
                _testConnectionState.value = UiState.Error(friendlyError(e))
            }
        }
    }

    fun resetTestConnectionState() {
        _testConnectionState.value = UiState.Idle
    }
}
