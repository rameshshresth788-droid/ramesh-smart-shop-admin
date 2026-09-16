import os

pkg_dir = 'app/src/main/java/com/example'

main_activity_kt = """
package com.example

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.example.data.Product
import com.example.ui.AdminViewModel
import com.example.ui.UiState
import com.example.ui.theme.Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Theme {
                val navController = rememberNavController()
                val token by viewModel.adminToken.collectAsState(initial = "")
                
                LaunchedEffect(token) {
                    if (token.isEmpty()) navController.navigate("login") { popUpTo(0) }
                    else navController.navigate("dashboard") { popUpTo(0) }
                }

                NavHost(navController, startDestination = if (token.isEmpty()) "login" else "dashboard") {
                    composable("login") { LoginScreen(viewModel) }
                    composable("dashboard") { DashboardScreen(navController, viewModel) }
                    composable("add_product") { AddProductScreen(navController, viewModel) }
                    composable("products") { ProductsScreen(navController, viewModel) }
                    composable("requests") { RequestsScreen(navController, viewModel) }
                    composable("purchases") { PurchasesScreen(navController, viewModel) }
                    composable("settings") { SettingsScreen(navController, viewModel) }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: AdminViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Admin Login", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { viewModel.login(username, password) }, modifier = Modifier.fillMaxWidth()) {
                if (loginState is UiState.Loading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                else Text("Login")
            }
            if (loginState is UiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text((loginState as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val state by viewModel.dashboardState.collectAsState()
    
    LaunchedEffect(Unit) { viewModel.loadDashboard() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Dashboard") }, actions = {
            IconButton(onClick = { viewModel.logout() }) { Icon(Icons.Default.ExitToApp, "Logout") }
        }) },
        bottomBar = { BottomNav(navController) }
    ) { padding ->
        when (state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Success -> {
                val data = (state as UiState.Success).data
                Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DashboardCard("Pending Requests", data.pending_requests.toString(), Modifier.weight(1f))
                        DashboardCard("Completed Today", data.completed_today.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DashboardCard("Total Products", data.total_products.toString(), Modifier.weight(1f))
                        DashboardCard("Today's Sales", "₹${data.todays_sales}", Modifier.weight(1f))
                    }
                }
            }
            is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text((state as UiState.Error).message) }
            else -> {}
        }
    }
}

@Composable
fun DashboardCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val aiState by viewModel.aiAnalysisState.collectAsState()
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            }
            bitmap?.let { b -> viewModel.analyzeProductImage(b) }
        }
    }
    
    LaunchedEffect(aiState) {
        if (aiState is UiState.Success) {
            val data = (aiState as UiState.Success).data
            name = data["product_name"] ?: ""
            category = data["category"] ?: ""
            description = data["description"] ?: ""
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Add Product") }) },
        bottomBar = { BottomNav(navController) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Button(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("Select Product Image")
            }
            Spacer(Modifier.height(16.dp))
            
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.height(200.dp).fillMaxWidth()) }
            
            if (aiState is UiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("AI Analyzing...", modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Kitna price rakhna hai? (₹)") }, modifier = Modifier.fillMaxWidth())
            
            Spacer(Modifier.height(32.dp))
            Button(onClick = {
                val p = price.toDoubleOrNull() ?: 0.0
                viewModel.saveProduct(Product(0, name, category, description, null, p, "", 1))
                navController.navigate("products") { popUpTo("dashboard") }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("SAVE PRODUCT")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val state by viewModel.productsState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadProducts() }
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("Products") }) },
        bottomBar = { BottomNav(navController) }
    ) { padding ->
        when (state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Success -> {
                val products = (state as UiState.Success).data
                LazyColumn(Modifier.padding(padding)) {
                    items(products) { product ->
                        Card(Modifier.padding(8.dp).fillMaxWidth()) {
                            Row(Modifier.padding(16.dp)) {
                                AsyncImage(model = product.image_url, contentDescription = null, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(product.product_name, style = MaterialTheme.typography.titleMedium)
                                    Text(product.category)
                                    Text("₹${product.current_price}")
                                }
                                IconButton(onClick = { viewModel.deleteProduct(product.id) }) { Icon(Icons.Default.Delete, "Delete") }
                            }
                        }
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error loading products") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val state by viewModel.requestsState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadRequests() }
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("User Requests") }) },
        bottomBar = { BottomNav(navController) }
    ) { padding ->
        when (state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Success -> {
                val requests = (state as UiState.Success).data
                LazyColumn(Modifier.padding(padding)) {
                    items(requests) { request ->
                        Card(Modifier.padding(8.dp).fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Customer: ${request.customer_name} (${request.gender})", style = MaterialTheme.typography.titleMedium)
                                Text("Total: ₹${request.total_amount}", color = MaterialTheme.colorScheme.primary)
                                request.items.forEach { item ->
                                    Text("${item.product_name} x${item.quantity}")
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { viewModel.cancelRequest(request.id) }) { Text("CANCEL") }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = { viewModel.completeRequest(request.id) }) { Text("COMPLETE PURCHASE") }
                                }
                            }
                        }
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error loading requests") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val state by viewModel.purchasesState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadPurchases() }
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("Purchase History (1-Day)") }) },
        bottomBar = { BottomNav(navController) }
    ) { padding ->
        when (state) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Success -> {
                val purchases = (state as UiState.Success).data
                LazyColumn(Modifier.padding(padding)) {
                    items(purchases) { purchase ->
                        Card(Modifier.padding(8.dp).fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Customer: ${purchase.customer_name}", style = MaterialTheme.typography.titleMedium)
                                Text("Total: ₹${purchase.total_amount}")
                                Text("Completed at: ${purchase.created_at}", style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(onClick = { viewModel.deletePurchase(purchase.id) }) { Icon(Icons.Default.Delete, "Delete") }
                                }
                            }
                        }
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error loading purchases") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController, viewModel: AdminViewModel) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    
    val currentUrl by viewModel.apiBaseUrl.collectAsState(initial = "")
    val currentAiKey by viewModel.aiApiKey.collectAsState(initial = "")
    val currentAiModel by viewModel.aiModel.collectAsState(initial = "")
    
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var aiKey by remember(currentAiKey) { mutableStateOf(currentAiKey) }
    var aiModel by remember(currentAiModel) { mutableStateOf(currentAiModel) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        bottomBar = { BottomNav(navController) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Backend/API Settings", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth())
            
            Spacer(Modifier.height(24.dp))
            Text("AI Settings", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = aiKey, onValueChange = { aiKey = it }, label = { Text("Gemini API Key (Required)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = aiModel, onValueChange = { aiModel = it }, label = { Text("Model (e.g. gemini-3.5-flash)") }, modifier = Modifier.fillMaxWidth())
            
            Spacer(Modifier.height(32.dp))
            Button(onClick = {
                scope.launch { viewModel.saveSettings(url, aiKey, aiModel) }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("SAVE SETTINGS")
            }
        }
    }
}

@Composable
fun BottomNav(navController: NavHostController) {
    NavigationBar {
        val currentRoute = navController.currentDestination?.route
        NavigationBarItem(selected = currentRoute == "dashboard", onClick = { navController.navigate("dashboard") }, icon = { Icon(Icons.Default.Home, "Dashboard") }, label = { Text("Home") })
        NavigationBarItem(selected = currentRoute == "add_product", onClick = { navController.navigate("add_product") }, icon = { Icon(Icons.Default.Add, "Add") }, label = { Text("Add") })
        NavigationBarItem(selected = currentRoute == "products", onClick = { navController.navigate("products") }, icon = { Icon(Icons.Default.List, "Products") }, label = { Text("Products") })
        NavigationBarItem(selected = currentRoute == "requests", onClick = { navController.navigate("requests") }, icon = { Icon(Icons.Default.Notifications, "Requests") }, label = { Text("Requests") })
        NavigationBarItem(selected = currentRoute == "purchases", onClick = { navController.navigate("purchases") }, icon = { Icon(Icons.Default.ShoppingCart, "History") }, label = { Text("History") })
        NavigationBarItem(selected = currentRoute == "settings", onClick = { navController.navigate("settings") }, icon = { Icon(Icons.Default.Settings, "Settings") }, label = { Text("Settings") })
    }
}
"""
with open(f'{pkg_dir}/MainActivity.kt', 'w') as f:
    f.write(main_activity_kt.strip() + '\n')

print("MainActivity replaced.")
