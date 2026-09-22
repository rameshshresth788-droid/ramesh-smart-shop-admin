import os

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content.strip() + '\n')

pkg_dir = 'app/src/main/java/com/example'

viewmodel_kt = """
package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiClient
import com.example.api.GeminiContent
import com.example.api.GeminiInlineData
import com.example.api.GeminiPart
import com.example.api.GeminiRequest
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.Exception

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    
    val apiBaseUrl = settingsManager.apiBaseUrl
    val adminToken = settingsManager.adminToken
    val aiApiKey = settingsManager.aiApiKey
    val aiModel = settingsManager.aiModel

    private val _loginState = MutableStateFlow<UiState<Boolean>>(UiState.Idle)
    val loginState: StateFlow<UiState<Boolean>> = _loginState

    private val _dashboardState = MutableStateFlow<UiState<DashboardData>>(UiState.Idle)
    val dashboardState: StateFlow<UiState<DashboardData>> = _dashboardState

    private val _productsState = MutableStateFlow<UiState<List<Product>>>(UiState.Idle)
    val productsState: StateFlow<UiState<List<Product>>>(UiState.Idle) = _productsState

    private val _requestsState = MutableStateFlow<UiState<List<PurchaseRequest>>>(UiState.Idle)
    val requestsState: StateFlow<UiState<List<PurchaseRequest>>>(UiState.Idle) = _requestsState

    private val _purchasesState = MutableStateFlow<UiState<List<PurchaseRequest>>>(UiState.Idle)
    val purchasesState: StateFlow<UiState<List<PurchaseRequest>>>(UiState.Idle) = _purchasesState

    private val _aiAnalysisState = MutableStateFlow<UiState<Map<String, String>>>(UiState.Idle)
    val aiAnalysisState: StateFlow<UiState<Map<String, String>>> = _aiAnalysisState

    private suspend fun getApi() = ApiClient.getBackendApi(apiBaseUrl.first(), adminToken.first())

    fun login(username: String, pass: String) {
        viewModelScope.launch {
            _loginState.value = UiState.Loading
            try {
                val api = ApiClient.getBackendApi(apiBaseUrl.first(), "")
                val res = api.login(mapOf("username" to username, "password" to pass))
                if (res.success && res.data != null) {
                    settingsManager.saveAdminToken(res.data["token"] ?: "")
                    _loginState.value = UiState.Success(true)
                } else {
                    _loginState.value = UiState.Error(res.message)
                }
            } catch (e: Exception) {
                _loginState.value = UiState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            settingsManager.logout()
            _loginState.value = UiState.Idle
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _dashboardState.value = UiState.Loading
            try {
                val res = getApi().getDashboard()
                if (res.success && res.data != null) _dashboardState.value = UiState.Success(res.data)
                else _dashboardState.value = UiState.Error(res.message)
            } catch (e: Exception) {
                _dashboardState.value = UiState.Error(e.message ?: "Error loading dashboard")
            }
        }
    }

    fun loadProducts() {
        viewModelScope.launch {
            _productsState.value = UiState.Loading
            try {
                val res = getApi().getProducts()
                if (res.success && res.data != null) _productsState.value = UiState.Success(res.data)
                else _productsState.value = UiState.Error(res.message)
            } catch (e: Exception) {
                _productsState.value = UiState.Error(e.message ?: "Error loading products")
            }
        }
    }

    fun saveProduct(product: Product) {
        viewModelScope.launch {
            try {
                if (product.id == 0) getApi().addProduct(product)
                else getApi().updateProduct(product.id, product)
                loadProducts()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun deleteProduct(id: Int) {
        viewModelScope.launch {
            try {
                getApi().deleteProduct(id)
                loadProducts()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun loadRequests() {
        viewModelScope.launch {
            _requestsState.value = UiState.Loading
            try {
                val res = getApi().getRequests()
                if (res.success && res.data != null) _requestsState.value = UiState.Success(res.data)
                else _requestsState.value = UiState.Error(res.message)
            } catch (e: Exception) {
                _requestsState.value = UiState.Error(e.message ?: "Error loading requests")
            }
        }
    }

    fun completeRequest(id: Int) {
        viewModelScope.launch {
            try {
                getApi().updateRequestStatus(id, mapOf("status" to "COMPLETED"))
                loadRequests()
            } catch (e: Exception) {
                // handle error
            }
        }
    }
    
    fun cancelRequest(id: Int) {
        viewModelScope.launch {
            try {
                getApi().updateRequestStatus(id, mapOf("status" to "CANCELLED"))
                loadRequests()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun loadPurchases() {
        viewModelScope.launch {
            _purchasesState.value = UiState.Loading
            try {
                val res = getApi().getPurchases()
                if (res.success && res.data != null) _purchasesState.value = UiState.Success(res.data)
                else _purchasesState.value = UiState.Error(res.message)
            } catch (e: Exception) {
                _purchasesState.value = UiState.Error(e.message ?: "Error loading purchases")
            }
        }
    }

    fun deletePurchase(id: Int) {
        viewModelScope.launch {
            try {
                getApi().deletePurchase(id)
                loadPurchases()
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun analyzeProductImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _aiAnalysisState.value = UiState.Loading
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                
                val prompt = "Analyze this product image. Return a JSON object containing: product_name, category, description."
                
                val req = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = prompt),
                                GeminiPart(inlineData = GeminiInlineData("image/jpeg", base64))
                            )
                        )
                    ),
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "You are a product analyzer. Return ONLY JSON without markdown.")))
                )
                
                val api = ApiClient.getGeminiApi()
                val model = aiModel.first()
                val key = aiApiKey.first()
                if(key.isEmpty()) {
                    _aiAnalysisState.value = UiState.Error("AI API Key not configured in Settings.")
                    return@launch
                }
                val res = api.generateContent("v1beta/models/$model:generateContent", key, req)
                val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                
                // parse JSON
                val cleanText = text.replace("```json", "").replace("```", "").trim()
                val json = JSONObject(cleanText)
                val map = mapOf(
                    "product_name" to json.optString("product_name"),
                    "category" to json.optString("category"),
                    "description" to json.optString("description")
                )
                _aiAnalysisState.value = UiState.Success(map)
            } catch (e: Exception) {
                _aiAnalysisState.value = UiState.Error("AI Analysis failed: ${e.message}")
            }
        }
    }

    fun resetAiState() {
        _aiAnalysisState.value = UiState.Idle
    }

    suspend fun saveSettings(url: String, aiKey: String, aiModel: String) {
        settingsManager.saveApiBaseUrl(url)
        settingsManager.saveAiSettings("Gemini", aiKey, aiModel)
    }
}
"""
write_file(f'{pkg_dir}/ui/AdminViewModel.kt', viewmodel_kt)
print("Generated AdminViewModel")
