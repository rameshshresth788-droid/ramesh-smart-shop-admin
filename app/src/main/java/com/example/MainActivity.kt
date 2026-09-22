package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.example.data.Product
import com.example.data.StaffAdmin
import com.example.data.StaffEditRequest
import com.example.ui.AdminViewModel
import com.example.ui.ProductDraft
import com.example.ui.UiState
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ImageUtils
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val token by viewModel.adminToken.collectAsState(initial = null)
                val adminRole by viewModel.adminRole.collectAsState(initial = "")


                LaunchedEffect(token) {
                    if (token != null) {
                        if (token!!.isEmpty()) navController.navigate("login") { popUpTo(0) }
                        else navController.navigate("dashboard") { popUpTo(0) }
                    }
                }

                if (token == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    NavHost(navController, startDestination = if (token!!.isEmpty()) "login" else "dashboard") {
                        composable("login") { LoginScreen(navController, viewModel) }
                        composable("staff_register") { StaffRegisterScreen(navController, viewModel) }
                        composable("connection_settings") { ConnectionSettingsScreen(navController, viewModel) }
                        composable("dashboard") { DashboardScreen(navController, viewModel) }
                        composable("add_product") { AddProductScreen(navController, viewModel) }
                        composable("products") { ProductsScreen(navController, viewModel) }
                        composable("requests") { RequestsScreen(navController, viewModel) }
                        composable("purchases") { PurchasesScreen(navController, viewModel) }
                        // Head-Admin-only routes: bottom nav already hides these tabs for staff,
                        // but RequireHeadAdmin() also guards the route itself so a staff member
                        // can never reach these screens by deep link, back-stack restore, or any
                        // other direct navigation path.
                        composable("settings") {
                            RequireHeadAdmin(navController, viewModel) { SettingsScreen(navController, viewModel) }
                        }
                        composable("staff") {
                            RequireHeadAdmin(navController, viewModel) { StaffManagementScreen(navController, viewModel) }
                        }
                        composable("staff_detail/{staffId}") { backStackEntry ->
                            val staffId = backStackEntry.arguments?.getString("staffId")?.toIntOrNull() ?: 0
                            RequireHeadAdmin(navController, viewModel) { StaffDetailScreen(navController, viewModel, staffId) }
                        }

                    }
                }
            }
        }
    }
}

// ============================================================= LOGIN =============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavHostController, viewModel: AdminViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    TextButton(onClick = { navController.navigate("connection_settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Settings / API URL")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Ramesh Smart Shop", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Admin Login", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { viewModel.login(username, password) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                if (loginState is UiState.Loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Login")
            }
            if (loginState is UiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text((loginState as UiState.Error).message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(20.dp))
            TextButton(onClick = { navController.navigate("staff_register") }) {
                Text("New staff member? Register here")
            }
        }
    }
}

// ============================================================ CONNECTION SETTINGS (PRE-LOGIN) ============================================================
// Unauthenticated screen: only touches the local API Base URL (DataStore) and the
// public, unauthenticated Test Connection call. Never calls any admin/settings
// endpoint and never requires a login token.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedUrl by viewModel.apiBaseUrl.collectAsState(initial = "")
    val testState by viewModel.testConnectionState.collectAsState()

    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(testState) {
        when (val t = testState) {
            is UiState.Success -> { Toast.makeText(context, t.data, Toast.LENGTH_SHORT).show(); viewModel.resetTestConnectionState() }
            is UiState.Error -> { Toast.makeText(context, t.message, Toast.LENGTH_LONG).show(); viewModel.resetTestConnectionState() }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text(
                "Set your backend server address before logging in. This is saved on this device only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it; saved = false },
                label = { Text("API Base URL") },
                placeholder = { Text("https://yourdomain.com/backend/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Example: https://yourdomain.com/backend/  (emulator default: http://10.0.2.2/.../backend/)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { scope.launch { viewModel.saveApiBaseUrl(url); saved = true } },
                    modifier = Modifier.weight(1f)
                ) { Text("SAVE URL") }

                OutlinedButton(
                    onClick = { viewModel.testConnection(url) },
                    enabled = testState !is UiState.Loading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (testState is UiState.Loading) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Text("TEST CONNECTION")
                }
            }

            if (saved) {
                Spacer(Modifier.height(12.dp))
                Text("URL saved on this device.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(28.dp))
            OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back to Login")
            }
        }
    }
}

// ============================================================ DASHBOARD ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val state by viewModel.dashboardState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadDashboard() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Dashboard") }, actions = {
                IconButton(onClick = { viewModel.logout() }) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout") }
            })
        },
        bottomBar = { BottomNav(navController, viewModel) }
    ) { padding ->
        when (val s = state) {
            is UiState.Loading, UiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Success -> {
                val data = s.data
                Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DashboardCard("Pending Requests", data.pending_requests.toString(), Modifier.weight(1f))
                        DashboardCard("Completed Today", data.completed_today.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DashboardCard("Active Products", "${data.active_products}/${data.total_products}", Modifier.weight(1f))
                        DashboardCard("Today's Sales", "\u20B9${data.todays_sales}", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { navController.navigate("add_product") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(8.dp)); Text("Add New Product")
                    }
                }
            }
            is UiState.Error -> ErrorBox(s.message) { viewModel.loadDashboard() }
        }
    }
}

@Composable
fun DashboardCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

// ========================================================== ADD / EDIT PRODUCT ==========================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val context = LocalContext.current
    val draft by viewModel.productDraft.collectAsState()
    val aiState by viewModel.aiAnalysisState.collectAsState()
    val saveState by viewModel.saveProductState.collectAsState()

    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // AI Analyze toggle: ON lets the backend AI suggest name/category/type/description from
    // the photo; OFF skips the AI call entirely and the admin fills every field in by hand.
    // Either way the product can be saved - an AI failure never blocks manual saving.
    var aiAnalyzeEnabled by remember { mutableStateOf(true) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            val bmp = ImageUtils.loadBitmapFixOrientation(context, uri)
            if (bmp != null) viewModel.onImageSelected(uri.toString(), bmp, aiAnalyzeEnabled)
            else errorMessage = "Could not read the captured photo. Please try again."
        } else if (!success) {
            errorMessage = "Camera capture was cancelled."
        }
    }

    fun tryLaunchCamera() {
        try {
            val uri = ImageUtils.createCaptureUri(context)
            pendingCaptureUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            errorMessage = "Camera is not available on this device. Please use Gallery instead."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            tryLaunchCamera()
        } else {
            errorMessage = "Camera permission was denied. You can still use Gallery, or allow Camera access from phone Settings."
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bmp = ImageUtils.loadBitmapFixOrientation(context, uri)
            if (bmp != null) viewModel.onImageSelected(uri.toString(), bmp, aiAnalyzeEnabled)
            else errorMessage = "Could not read the selected image."
        }
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) tryLaunchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(saveState) {
        if (saveState is UiState.Success) {
            Toast.makeText(context, "Product saved", Toast.LENGTH_SHORT).show()
            navController.navigate("products") { popUpTo("dashboard") }
        }
    }

    errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            errorMessage = null
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (draft.id == 0) "Add Product" else "Edit Product") }) },
        bottomBar = { BottomNav(navController, viewModel) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {

            // ---------- Image area ----------
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val previewModel = draft.imageUri ?: draft.imageUrl
                    if (previewModel != null) {
                        AsyncImage(model = previewModel, contentDescription = null, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text("No photo yet", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    if (draft.imageUri != null) {
                        TextButton(
                            onClick = { viewModel.retakePhoto() },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                            colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text("Retake")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Camera")
                }
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Gallery")
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI Analyze", fontWeight = FontWeight.Medium)
                    Text(
                        "When off, no AI call is made - fill in details manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = aiAnalyzeEnabled, onCheckedChange = { aiAnalyzeEnabled = it })
            }

            if (aiState is UiState.Loading) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI analyzing photo\u2026")
                }
            }
            if (aiState is UiState.Error) {
                Spacer(Modifier.height(12.dp))
                Text("AI analysis unavailable: ${(aiState as UiState.Error).message}. You can fill in details manually.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            val confidence = draft.aiConfidence
            if (confidence != null && aiState is UiState.Success) {
                Spacer(Modifier.height(8.dp))
                AssistChip(onClick = {}, label = { Text("AI confidence: ${confidence.replaceFirstChar { it.uppercase() }}") })
            }

            Spacer(Modifier.height(20.dp))
            Text("Product Details \u2014 edit anything the AI got wrong", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(value = draft.productName, onValueChange = { v -> viewModel.updateDraftField { it.copy(productName = v) } }, label = { Text("Product Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = draft.category, onValueChange = { v -> viewModel.updateDraftField { it.copy(category = v) } }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = draft.productType, onValueChange = { v -> viewModel.updateDraftField { it.copy(productType = v) } }, label = { Text("Product Type") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = draft.description, onValueChange = { v -> viewModel.updateDraftField { it.copy(description = v) } }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active on website", modifier = Modifier.weight(1f))
                Switch(checked = draft.status == "ACTIVE", onCheckedChange = { checked ->
                    viewModel.updateDraftField { it.copy(status = if (checked) "ACTIVE" else "INACTIVE") }
                })
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = draft.price,
                onValueChange = { v -> viewModel.updateDraftField { it.copy(price = v.filter { c -> c.isDigit() || c == '.' }) } },
                label = { Text("Kitna price rakhna hai? (\u20B9)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (saveState is UiState.Error) {
                Spacer(Modifier.height(12.dp))
                Text((saveState as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = { viewModel.saveProduct() }, enabled = saveState !is UiState.Loading, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                if (saveState is UiState.Loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("SAVE PRODUCT")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================================================ PRODUCTS ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val state by viewModel.productsState.collectAsState()
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    LaunchedEffect(Unit) { viewModel.loadProducts() }

    productToDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Delete Product") },
            text = { Text("Delete \"${product.product_name}\"? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteProduct(product.id); productToDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { productToDelete = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Products") }, actions = {
                IconButton(onClick = { viewModel.startNewProduct(); navController.navigate("add_product") }) { Icon(Icons.Default.Add, "Add") }
            })
        },
        bottomBar = { BottomNav(navController, viewModel) }
    ) { padding ->
        when (val s = state) {
            is UiState.Loading, UiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("No products yet. Tap + to add one.") }
                } else {
                    LazyColumn(Modifier.padding(padding)) {
                        items(s.data) { product ->
                            Card(Modifier.padding(8.dp).fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(model = product.image_url, contentDescription = null, modifier = Modifier.size(64.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(product.product_name, style = MaterialTheme.typography.titleMedium)
                                        Text(product.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("\u20B9${product.current_price}", fontWeight = FontWeight.Bold)
                                            if (product.old_price != null) {
                                                Spacer(Modifier.width(6.dp))
                                                Text("\u20B9${product.old_price}", style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        AssistChip(onClick = { viewModel.toggleProductStatus(product) }, label = { Text(product.status) })
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        IconButton(onClick = { viewModel.startEditProduct(product); navController.navigate("add_product") }) { Icon(Icons.Default.Edit, "Edit") }
                                        IconButton(onClick = { productToDelete = product }) { Icon(Icons.Default.Delete, "Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Error -> ErrorBox(s.message) { viewModel.loadProducts() }
        }
    }
}

// ============================================================ REQUESTS ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val state by viewModel.requestsState.collectAsState()
    var confirmCompleteId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { viewModel.loadRequests() }

    confirmCompleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmCompleteId = null },
            title = { Text("Complete Purchase") },
            text = { Text("Payment received and products delivered?") },
            confirmButton = { Button(onClick = { viewModel.completeRequest(id); confirmCompleteId = null }) { Text("COMPLETE PURCHASE") } },
            dismissButton = { TextButton(onClick = { confirmCompleteId = null }) { Text("CANCEL") } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("User Requests") }) },
        bottomBar = { BottomNav(navController, viewModel) }
    ) { padding ->
        when (val s = state) {
            is UiState.Loading, UiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("No pending requests right now.") }
                } else {
                    LazyColumn(Modifier.padding(padding)) {
                        items(s.data) { request ->
                            Card(Modifier.padding(8.dp).fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("${request.customer_name}${request.gender?.let { " ($it)" } ?: ""}", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(4.dp))
                                    request.items.forEach { item ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(model = item.image_url, contentDescription = item.product_name, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(item.product_name, fontWeight = FontWeight.Medium)
                                                Text("Qty: ${item.quantity}  •  ₹${item.subtotal}", style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("Total: \u20B9${request.total_amount}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { viewModel.cancelRequest(request.id) }) { Text("CANCEL") }
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = { confirmCompleteId = request.id }) { Text("COMPLETE PURCHASE") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Error -> ErrorBox(s.message) { viewModel.loadRequests() }
        }
    }
}

// ============================================================ PURCHASE HISTORY ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val state by viewModel.purchasesState.collectAsState()
    var toDelete by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { viewModel.loadPurchases() }

    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete purchase record?") },
            confirmButton = { TextButton(onClick = { viewModel.deletePurchase(id); toDelete = null }) { Text("DELETE NOW") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Purchase History (24h)") }) },
        bottomBar = { BottomNav(navController, viewModel) }
    ) { padding ->
        when (val s = state) {
            is UiState.Loading, UiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("No completed purchases in the last 24 hours.") }
                } else {
                    LazyColumn(Modifier.padding(padding)) {
                        items(s.data) { purchase ->
                            Card(Modifier.padding(8.dp).fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(purchase.customer_name, style = MaterialTheme.typography.titleMedium)
                                    purchase.items.forEach { item -> Text("${item.product_name} x${item.quantity}") }
                                    Text("Total: \u20B9${purchase.total_amount}")
                                    Text("Completed at: ${purchase.completed_at}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { toDelete = purchase.id }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Delete Now") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Error -> ErrorBox(s.message) { viewModel.loadPurchases() }
        }
    }
}

// ============================================================ SETTINGS ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUrl by viewModel.apiBaseUrl.collectAsState(initial = "")
    val settingsState by viewModel.settingsState.collectAsState()
    val testState by viewModel.testConnectionState.collectAsState()

    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var shopName by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("") }
    var historyHours by remember { mutableStateOf("24") }
    var expiryMinutes by remember { mutableStateOf("120") }
    var aiProvider by remember { mutableStateOf("gemini") }
    var aiModel by remember { mutableStateOf("") }
    var aiEndpoint by remember { mutableStateOf("") }
    var aiApiKey by remember { mutableStateOf("") }
    var aiKeyIsSet by remember { mutableStateOf(false) }
    var cloudName by remember { mutableStateOf("") }
    var cloudApiKey by remember { mutableStateOf("") }
    var cloudApiSecret by remember { mutableStateOf("") }
    var cloudSecretIsSet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadSettings() }
    LaunchedEffect(settingsState) {
        val s = settingsState
        if (s is UiState.Success) {
            val d = s.data
            shopName = (d["shop_name"] as? String) ?: shopName
            currencySymbol = (d["currency_symbol"] as? String) ?: currencySymbol
            historyHours = (d["purchase_history_hours"] as? String) ?: historyHours
            expiryMinutes = (d["request_expiry_minutes"] as? String) ?: expiryMinutes
            aiProvider = (d["ai_provider"] as? String) ?: aiProvider
            aiModel = (d["ai_model"] as? String) ?: aiModel
            aiEndpoint = (d["ai_endpoint"] as? String) ?: aiEndpoint
            aiKeyIsSet = (d["ai_api_key_is_set"] as? Boolean) ?: false
            cloudName = (d["cloudinary_cloud_name"] as? String) ?: cloudName
            cloudSecretIsSet = (d["cloudinary_api_secret_is_set"] as? Boolean) ?: false
        }
    }
    LaunchedEffect(testState) {
        when (val t = testState) {
            is UiState.Success -> { Toast.makeText(context, t.data, Toast.LENGTH_SHORT).show(); viewModel.resetTestConnectionState() }
            is UiState.Error -> { Toast.makeText(context, t.message, Toast.LENGTH_LONG).show(); viewModel.resetTestConnectionState() }
            else -> {}
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        bottomBar = { BottomNav(navController, viewModel) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {

            SettingsSectionTitle("Backend")
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("API Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { viewModel.saveApiBaseUrl(url) } }, modifier = Modifier.weight(1f)) { Text("Save URL") }
                OutlinedButton(onClick = { viewModel.testConnection(url) }, enabled = testState !is UiState.Loading, modifier = Modifier.weight(1f)) {
                    if (testState is UiState.Loading) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("TEST CONNECTION")
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle("AI (Product Analysis)")
            OutlinedTextField(value = aiProvider, onValueChange = { aiProvider = it }, label = { Text("AI Provider (gemini)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = aiModel, onValueChange = { aiModel = it }, label = { Text("Model (e.g. gemini-2.5-flash)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = aiEndpoint, onValueChange = { aiEndpoint = it }, label = { Text("Custom AI Endpoint (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = aiApiKey, onValueChange = { aiApiKey = it },
                label = { Text(if (aiKeyIsSet) "AI API Key (saved \u2713 - leave blank to keep)" else "AI API Key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
            )
            Text("Stored on the server only. Never saved on this phone or sent to the website.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle("Cloudinary (optional - product image hosting)")
            OutlinedTextField(value = cloudName, onValueChange = { cloudName = it }, label = { Text("Cloud Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = cloudApiKey, onValueChange = { cloudApiKey = it }, label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cloudApiSecret, onValueChange = { cloudApiSecret = it },
                label = { Text(if (cloudSecretIsSet) "API Secret (saved \u2713 - leave blank to keep)" else "API Secret") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
            )
            Text("If left empty/not configured, product photos are stored on your own server instead.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(24.dp))
            SettingsSectionTitle("Shop")
            OutlinedTextField(value = shopName, onValueChange = { shopName = it }, label = { Text("Shop Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = currencySymbol, onValueChange = { currencySymbol = it }, label = { Text("Currency Symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = historyHours, onValueChange = { historyHours = it.filter { c -> c.isDigit() } }, label = { Text("Purchase History Duration (hours)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = expiryMinutes, onValueChange = { expiryMinutes = it.filter { c -> c.isDigit() } }, label = { Text("Request Expiry Duration (minutes)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            if (settingsState is UiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((settingsState as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch { viewModel.saveApiBaseUrl(url) }
                    viewModel.saveBackendSettings(
                        mapOf(
                            "shop_name" to shopName,
                            "currency_symbol" to currencySymbol,
                            "purchase_history_hours" to historyHours.ifBlank { "24" },
                            "request_expiry_minutes" to expiryMinutes.ifBlank { "120" },
                            "ai_provider" to aiProvider,
                            "ai_model" to aiModel,
                            "ai_endpoint" to aiEndpoint,
                            "ai_api_key" to aiApiKey,
                            "cloudinary_cloud_name" to cloudName,
                            "cloudinary_api_key" to cloudApiKey,
                            "cloudinary_api_secret" to cloudApiSecret
                        )
                    )
                    aiApiKey = ""; cloudApiSecret = ""
                }, modifier = Modifier.weight(1f)) { Text("SAVE SETTINGS") }

                OutlinedButton(onClick = { viewModel.loadSettings() }, modifier = Modifier.weight(1f)) { Text("RESET") }
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { viewModel.logout() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text("LOGOUT")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

// ============================================================ NAV ============================================================

@Composable
fun BottomNav(navController: NavHostController, viewModel: AdminViewModel) {
    val adminRole by viewModel.adminRole.collectAsState(initial = "")
    fun go(route: String) {
        navController.navigate(route) {
            popUpTo("dashboard") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    NavigationBar {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        NavigationBarItem(selected = currentRoute == "dashboard", onClick = { go("dashboard") }, icon = { Icon(Icons.Default.Home, "Dashboard") }, label = { Text("Home") })
        NavigationBarItem(selected = currentRoute == "products", onClick = { go("products") }, icon = { Icon(Icons.Default.List, "Products") }, label = { Text("Products") })
        NavigationBarItem(selected = currentRoute == "requests", onClick = { go("requests") }, icon = { Icon(Icons.Default.Notifications, "Requests") }, label = { Text("Requests") })
        NavigationBarItem(selected = currentRoute == "purchases", onClick = { go("purchases") }, icon = { Icon(Icons.Default.History, "History") }, label = { Text("History") })
        if (adminRole == "HEAD_ADMIN") { NavigationBarItem(selected = currentRoute == "staff", onClick = { go("staff") }, icon = { Icon(Icons.Default.People, "Staff") }, label = { Text("Staff") }) }
        if (adminRole == "HEAD_ADMIN") { NavigationBarItem(selected = currentRoute == "settings", onClick = { go("settings") }, icon = { Icon(Icons.Default.Settings, "Settings") }, label = { Text("Settings") }) }
    }
}

/**
 * Route-level guard for Head-Admin-only screens. Renders [content] only when
 * the locally-stored role is HEAD_ADMIN; otherwise immediately navigates
 * back to the dashboard. This is defense-in-depth on top of hiding the
 * bottom-nav tabs - every mutating action these screens trigger is *also*
 * re-checked server-side against the real database role (requireHeadAdmin),
 * so this guard only prevents a confusing UI state, never acts as the
 * actual security boundary.
 */
@Composable
fun RequireHeadAdmin(
    navController: NavHostController,
    viewModel: AdminViewModel,
    content: @Composable () -> Unit
) {
    val adminRole by viewModel.adminRole.collectAsState(initial = "")
    when (adminRole) {
        "HEAD_ADMIN" -> content()
        "" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> {
            LaunchedEffect(Unit) {
                navController.navigate("dashboard") { popUpTo("dashboard") { inclusive = true } }
            }
        }
    }
}

/** Color + label for a staff approval_status badge, kept in one place so every screen agrees. */
@Composable
fun statusColor(status: String): Color = when (status) {
    "APPROVED" -> Color(0xFF2E7D32)
    "PENDING" -> Color(0xFFF9A825)
    "DISABLED" -> Color(0xFF757575)
    "REJECTED" -> Color(0xFFC62828)
    else -> MaterialTheme.colorScheme.outline
}

@Composable
fun StatusBadge(status: String) {
    val color = statusColor(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(status, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StaffManagementScreen(
    navController: NavHostController,
    viewModel: AdminViewModel
) {
    val staffState by viewModel.staffState.collectAsState()
    val actionState by viewModel.staffActionState.collectAsState()
    val context = LocalContext.current

    var pendingAction by remember { mutableStateOf<Pair<StaffAdmin, String>?>(null) }
    var staffToDelete by remember { mutableStateOf<StaffAdmin?>(null) }

    LaunchedEffect(Unit) { viewModel.loadStaff() }

    LaunchedEffect(actionState) {
        val s = actionState
        if (s is UiState.Success) {
            Toast.makeText(context, s.data, Toast.LENGTH_SHORT).show()
            viewModel.resetStaffActionState()
        } else if (s is UiState.Error) {
            Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
            viewModel.resetStaffActionState()
        }
    }

    // Confirmation dialog before any destructive/state-changing action.
    pendingAction?.let { (staff, action) ->
        val (title, message, confirmLabel) = when (action) {
            "approve" -> Triple("Approve staff?", "${staff.name} will be able to log in immediately.", "Approve")
            "reject" -> Triple("Reject staff?", "${staff.name} will not be able to log in. You can still delete or re-approve later.", "Reject")
            "disable" -> Triple("Disable staff?", "${staff.name} will be logged out and unable to log in until re-enabled.", "Disable")
            "enable" -> Triple("Enable staff?", "${staff.name} will be able to log in again.", "Enable")
            else -> Triple("Confirm", "Are you sure?", "Confirm")
        }
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.performStaffAction(staff.id, action); pendingAction = null }) { Text(confirmLabel) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } }
        )
    }

    staffToDelete?.let { staff ->
        AlertDialog(
            onDismissRequest = { staffToDelete = null },
            title = { Text("Delete staff account") },
            text = { Text("Permanently delete \"${staff.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteStaff(staff.id); staffToDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { staffToDelete = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff Management") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadStaff() }) { Icon(Icons.Default.Refresh, "Refresh") }
                }
            )
        },
        bottomBar = { BottomNav(navController, viewModel) }
    ) { padding ->
        when (val state = staffState) {
            is UiState.Loading, UiState.Idle -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadStaff() }) { Text("Retry") }
                    }
                }
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.People, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text("No staff accounts yet", color = MaterialTheme.colorScheme.outline)
                            Text("New staff registrations will show up here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(state.data, key = { it.id }) { staff ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { navController.navigate("staff_detail/${staff.id}") }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (staff.photo_url != null) {
                                            AsyncImage(
                                                model = staff.photo_url,
                                                contentDescription = "Staff photo",
                                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.outline)
                                            }
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(staff.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                "@${staff.username ?: "-"} • ${staff.phone}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "${staff.gender ?: "N/A"} • Age: ${staff.age ?: "N/A"} • Joined: ${staff.created_at?.take(10) ?: "N/A"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        StatusBadge(staff.approval_status)
                                    }

                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        when (staff.approval_status) {
                                            "PENDING" -> {
                                                Button(onClick = { pendingAction = staff to "approve" }, modifier = Modifier.weight(1f)) { Text("Approve") }
                                                OutlinedButton(onClick = { pendingAction = staff to "reject" }, modifier = Modifier.weight(1f)) { Text("Reject") }
                                            }
                                            "APPROVED" -> {
                                                OutlinedButton(onClick = { pendingAction = staff to "disable" }, modifier = Modifier.weight(1f)) { Text("Disable") }
                                                TextButton(onClick = { navController.navigate("staff_detail/${staff.id}") }, modifier = Modifier.weight(1f)) { Text("View / Edit") }
                                            }
                                            "DISABLED" -> {
                                                Button(onClick = { pendingAction = staff to "enable" }, modifier = Modifier.weight(1f)) { Text("Enable") }
                                                TextButton(onClick = { navController.navigate("staff_detail/${staff.id}") }, modifier = Modifier.weight(1f)) { Text("View / Edit") }
                                            }
                                            "REJECTED" -> {
                                                Button(onClick = { pendingAction = staff to "approve" }, modifier = Modifier.weight(1f)) { Text("Approve") }
                                                TextButton(onClick = { navController.navigate("staff_detail/${staff.id}") }, modifier = Modifier.weight(1f)) { Text("View / Edit") }
                                            }
                                        }
                                        IconButton(onClick = { staffToDelete = staff }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ STAFF DETAIL / EDIT ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffDetailScreen(navController: NavHostController, viewModel: AdminViewModel, staffId: Int) {
    val detailState by viewModel.staffDetailState.collectAsState()
    val actionState by viewModel.staffActionState.collectAsState()
    val context = LocalContext.current

    var editMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var geminiKey by remember { mutableStateOf("") }

    fun loadFieldsFrom(staff: StaffAdmin) {
        name = staff.name
        phone = staff.phone
        age = staff.age?.toString() ?: ""
        gender = staff.gender ?: ""
        username = staff.username ?: ""
        newPassword = ""
        geminiKey = ""
    }

    LaunchedEffect(staffId) { viewModel.loadStaffDetail(staffId) }
    LaunchedEffect(detailState) {
        val s = detailState
        if (s is UiState.Success && !editMode) loadFieldsFrom(s.data)
    }
    LaunchedEffect(actionState) {
        val s = actionState
        if (s is UiState.Success) {
            Toast.makeText(context, s.data, Toast.LENGTH_SHORT).show()
            viewModel.resetStaffActionState()
            editMode = false
        } else if (s is UiState.Error) {
            Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
            viewModel.resetStaffActionState()
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.clearStaffDetail() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editMode) "Edit Staff" else "Staff Details") },
                navigationIcon = {
                    IconButton(onClick = { if (editMode) editMode = false else navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    val s = detailState
                    if (s is UiState.Success && !editMode) {
                        IconButton(onClick = { loadFieldsFrom(s.data); editMode = true }) { Icon(Icons.Default.Edit, "Edit") }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = detailState) {
            is UiState.Loading, UiState.Idle -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(state.message, color = MaterialTheme.colorScheme.error) }
            is UiState.Success -> {
                val staff = state.data
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (staff.photo_url != null) {
                        AsyncImage(model = staff.photo_url, contentDescription = null, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
                    } else {
                        Box(
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline) }
                    }
                    Spacer(Modifier.height(8.dp))
                    StatusBadge(staff.approval_status)
                    Spacer(Modifier.height(20.dp))

                    if (!editMode) {
                        Column(Modifier.fillMaxWidth()) {
                            DetailRow("Name", staff.name)
                            DetailRow("Username", staff.username ?: "-")
                            DetailRow("Phone", staff.phone)
                            DetailRow("Age", staff.age?.toString() ?: "N/A")
                            DetailRow("Gender", staff.gender ?: "N/A")
                            DetailRow("Gemini API key", if (staff.has_gemini_key) "Set (hidden)" else "Not set")
                            DetailRow("Created", staff.created_at ?: "N/A")
                        }
                    } else {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = age, onValueChange = { v -> age = v.filter { it.isDigit() } }, label = { Text("Age") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("Gender (Male/Female/Other)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword, onValueChange = { newPassword = it },
                            label = { Text("New Password (leave blank to keep current)") },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = geminiKey, onValueChange = { geminiKey = it },
                            label = { Text(if (staff.has_gemini_key) "New Gemini API Key (leave blank to keep current)" else "Gemini API Key (optional)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                viewModel.updateStaffProfile(
                                    staff.id,
                                    StaffEditRequest(
                                        name = name.trim().ifBlank { null },
                                        phone = phone.trim().ifBlank { null },
                                        age = age.toIntOrNull(),
                                        gender = gender.trim().ifBlank { null },
                                        username = username.trim().ifBlank { null },
                                        password = newPassword.ifBlank { null },
                                        gemini_api_key = geminiKey.trim().ifBlank { null }
                                    )
                                )
                            },
                            enabled = actionState !is UiState.Loading,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            if (actionState is UiState.Loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            else Text("SAVE CHANGES")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1.4f), fontWeight = FontWeight.Medium)
    }
}

// ============================================================ STAFF REGISTRATION (PRE-LOGIN) ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffRegisterScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val context = LocalContext.current
    val registerState by viewModel.staffRegisterState.collectAsState()

    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var geminiKey by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> photoUri = uri }

    LaunchedEffect(registerState) {
        if (registerState is UiState.Success) {
            Toast.makeText(context, (registerState as UiState.Success).data, Toast.LENGTH_LONG).show()
            viewModel.resetStaffRegisterState()
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff Registration") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Your account needs Head Admin approval before you can log in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { galleryLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(model = photoUri, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = age, onValueChange = { v -> age = v.filter { it.isDigit() } }, label = { Text("Age") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("Gender (Male/Female/Other)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password *") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = geminiKey, onValueChange = { geminiKey = it }, label = { Text("Your Gemini API Key (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            if (registerState is UiState.Error) {
                Spacer(Modifier.height(12.dp))
                Text((registerState as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val photoFile = photoUri?.let { uri ->
                        ImageUtils.loadBitmapFixOrientation(context, uri)?.let { bmp -> ImageUtils.bitmapToTempFile(context, bmp) }
                    }
                    viewModel.registerStaff(name, age, gender, username, phone, password, geminiKey, photoFile)
                },
                enabled = registerState !is UiState.Loading,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (registerState is UiState.Loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("SUBMIT REGISTRATION")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
