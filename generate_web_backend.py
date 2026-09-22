import os
import json

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content.strip() + '\n')

backend_config_database_php = """
<?php
// backend/config/database.php
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With");

if ($_SERVER['REQUEST_METHOD'] == 'OPTIONS') {
    http_response_code(200);
    exit();
}

$host = 'localhost';
$db_name = 'ramesh_smart_shop';
$username = 'root';
$password = '';

try {
    $conn = new PDO("mysql:host=$host;dbname=$db_name;charset=utf8", $username, $password);
    $conn->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $conn->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
} catch(PDOException $e) {
    echo json_encode(["success" => false, "message" => "Connection Error: " . $e->getMessage()]);
    exit();
}

function sendJson($success, $message, $data = null) {
    echo json_encode(["success" => $success, "message" => $message, "data" => $data]);
    exit();
}

function verifyAdmin($conn) {
    $headers = apache_request_headers();
    $token = isset($headers['Authorization']) ? str_replace('Bearer ', '', $headers['Authorization']) : '';
    if (!$token) {
        sendJson(false, "Unauthorized: No token provided");
    }
    $stmt = $conn->prepare("SELECT id FROM admins WHERE token = ?");
    $stmt->execute([$token]);
    $admin = $stmt->fetch();
    if (!$admin) {
        sendJson(false, "Unauthorized: Invalid token");
    }
    return $admin['id'];
}
?>
"""

backend_api_admin_login_php = """
<?php
require_once '../../config/database.php';
if ($_SERVER['REQUEST_METHOD'] == 'POST') {
    $data = json_decode(file_get_contents("php://input"));
    $username = $data->username ?? '';
    $password = $data->password ?? '';
    
    $stmt = $conn->prepare("SELECT id, password FROM admins WHERE username = ? OR email = ?");
    $stmt->execute([$username, $username]);
    $admin = $stmt->fetch();
    
    if ($admin && password_verify($password, $admin['password'])) {
        $token = bin2hex(random_bytes(32));
        $stmt = $conn->prepare("UPDATE admins SET token = ? WHERE id = ?");
        $stmt->execute([$token, $admin['id']]);
        sendJson(true, "Login successful", ["token" => $token]);
    } else {
        sendJson(false, "Invalid credentials");
    }
}
?>
"""

backend_api_admin_dashboard_php = """
<?php
require_once '../../config/database.php';
verifyAdmin($conn);

$data = [];

$stmt = $conn->query("SELECT COUNT(*) as count FROM purchase_requests WHERE status = 'PENDING'");
$data['pending_requests'] = $stmt->fetch()['count'];

$stmt = $conn->query("SELECT COUNT(*) as count FROM purchase_requests WHERE status = 'COMPLETED' AND DATE(created_at) = CURDATE()");
$data['completed_today'] = $stmt->fetch()['count'];

$stmt = $conn->query("SELECT COUNT(*) as count FROM products WHERE active = 1");
$data['total_products'] = $stmt->fetch()['count'];

$stmt = $conn->query("SELECT SUM(total_amount) as total FROM purchase_requests WHERE status = 'COMPLETED' AND DATE(created_at) = CURDATE()");
$data['todays_sales'] = $stmt->fetch()['total'] ?: 0;

sendJson(true, "Dashboard data loaded", $data);
?>
"""

backend_api_products_index_php = """
<?php
require_once '../../config/database.php';

$method = $_SERVER['REQUEST_METHOD'];

if ($method == 'GET') {
    $id = $_GET['id'] ?? null;
    if ($id) {
        $stmt = $conn->prepare("SELECT * FROM products WHERE id = ?");
        $stmt->execute([$id]);
        sendJson(true, "Product loaded", $stmt->fetch());
    } else {
        $stmt = $conn->query("SELECT * FROM products ORDER BY id DESC");
        sendJson(true, "Products loaded", $stmt->fetchAll());
    }
} elseif ($method == 'POST') {
    verifyAdmin($conn);
    $data = json_decode(file_get_contents("php://input"));
    $stmt = $conn->prepare("INSERT INTO products (product_name, category, description, current_price, image_url, active) VALUES (?, ?, ?, ?, ?, 1)");
    $stmt->execute([$data->product_name, $data->category, $data->description, $data->current_price, $data->image_url]);
    sendJson(true, "Product added successfully", ["id" => $conn->lastInsertId()]);
} elseif ($method == 'PUT') {
    verifyAdmin($conn);
    $data = json_decode(file_get_contents("php://input"));
    $id = $_GET['id'] ?? null;
    if ($id) {
        // fetch old price
        $stmt = $conn->prepare("SELECT current_price FROM products WHERE id = ?");
        $stmt->execute([$id]);
        $old_product = $stmt->fetch();
        $old_price = $old_product['current_price'];
        if ($old_price != $data->current_price) {
            $stmt = $conn->prepare("UPDATE products SET product_name=?, category=?, description=?, old_price=?, current_price=?, image_url=?, active=? WHERE id=?");
            $stmt->execute([$data->product_name, $data->category, $data->description, $old_price, $data->current_price, $data->image_url, $data->active, $id]);
        } else {
            $stmt = $conn->prepare("UPDATE products SET product_name=?, category=?, description=?, image_url=?, active=? WHERE id=?");
            $stmt->execute([$data->product_name, $data->category, $data->description, $data->image_url, $data->active, $id]);
        }
        sendJson(true, "Product updated");
    }
} elseif ($method == 'DELETE') {
    verifyAdmin($conn);
    $id = $_GET['id'] ?? null;
    if ($id) {
        $stmt = $conn->prepare("DELETE FROM products WHERE id = ?");
        $stmt->execute([$id]);
        sendJson(true, "Product deleted");
    }
}
?>
"""

backend_api_requests_index_php = """
<?php
require_once '../../config/database.php';

$method = $_SERVER['REQUEST_METHOD'];

if ($method == 'POST') {
    $data = json_decode(file_get_contents("php://input"));
    $stmt = $conn->prepare("INSERT INTO purchase_requests (customer_name, gender, total_amount, status) VALUES (?, ?, ?, 'PENDING')");
    $stmt->execute([$data->customer_name, $data->gender, $data->total_amount]);
    $request_id = $conn->lastInsertId();
    
    foreach ($data->items as $item) {
        $stmt = $conn->prepare("INSERT INTO purchase_request_items (request_id, product_id, product_name, quantity, price) VALUES (?, ?, ?, ?, ?)");
        $stmt->execute([$request_id, $item->product_id, $item->product_name, $item->quantity, $item->price]);
    }
    sendJson(true, "Request sent to admin successfully", ["request_id" => $request_id]);
} elseif ($method == 'GET') {
    verifyAdmin($conn);
    $stmt = $conn->query("SELECT * FROM purchase_requests WHERE status = 'PENDING' ORDER BY created_at DESC");
    $requests = $stmt->fetchAll();
    foreach ($requests as &$req) {
        $stmt = $conn->prepare("SELECT * FROM purchase_request_items WHERE request_id = ?");
        $stmt->execute([$req['id']]);
        $req['items'] = $stmt->fetchAll();
    }
    sendJson(true, "Requests loaded", $requests);
} elseif ($method == 'PUT') {
    verifyAdmin($conn);
    $id = $_GET['id'] ?? null;
    $data = json_decode(file_get_contents("php://input"));
    $status = $data->status; // 'COMPLETED' or 'CANCELLED'
    if ($id && $status) {
        $stmt = $conn->prepare("UPDATE purchase_requests SET status = ? WHERE id = ?");
        $stmt->execute([$status, $id]);
        sendJson(true, "Request status updated to " . $status);
    }
}
?>
"""

backend_api_purchases_index_php = """
<?php
require_once '../../config/database.php';
verifyAdmin($conn);

$method = $_SERVER['REQUEST_METHOD'];

// Auto cleanup history older than 24h
$conn->query("DELETE FROM purchase_requests WHERE status = 'COMPLETED' AND created_at < NOW() - INTERVAL 1 DAY");

if ($method == 'GET') {
    $stmt = $conn->query("SELECT * FROM purchase_requests WHERE status = 'COMPLETED' ORDER BY created_at DESC");
    $purchases = $stmt->fetchAll();
    foreach ($purchases as &$req) {
        $stmt = $conn->prepare("SELECT * FROM purchase_request_items WHERE request_id = ?");
        $stmt->execute([$req['id']]);
        $req['items'] = $stmt->fetchAll();
    }
    sendJson(true, "Purchases loaded", $purchases);
} elseif ($method == 'DELETE') {
    $id = $_GET['id'] ?? null;
    if ($id) {
        $stmt = $conn->prepare("DELETE FROM purchase_requests WHERE id = ?");
        $stmt->execute([$id]);
        sendJson(true, "Purchase record deleted");
    }
}
?>
"""

backend_api_upload_index_php = """
<?php
require_once '../../config/database.php';
verifyAdmin($conn);

if ($_SERVER['REQUEST_METHOD'] == 'POST' && isset($_FILES['image'])) {
    $target_dir = "../../uploads/";
    if (!file_exists($target_dir)) {
        mkdir($target_dir, 0777, true);
    }
    
    $file_ext = strtolower(pathinfo($_FILES["image"]["name"], PATHINFO_EXTENSION));
    $new_name = uniqid() . '.' . $file_ext;
    $target_file = $target_dir . $new_name;
    
    // Only allow specific image formats
    $allowed = ['jpg', 'jpeg', 'png', 'webp'];
    if (in_array($file_ext, $allowed)) {
        if (move_uploaded_file($_FILES["image"]["tmp_name"], $target_file)) {
            // Return URL relative to backend root
            $url = "/backend/uploads/" . $new_name;
            sendJson(true, "Image uploaded", ["url" => $url]);
        } else {
            sendJson(false, "Failed to upload image");
        }
    } else {
        sendJson(false, "Invalid file type");
    }
}
?>
"""

backend_database_sql = """
CREATE DATABASE IF NOT EXISTS ramesh_smart_shop;
USE ramesh_smart_shop;

CREATE TABLE admins (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    token VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default admin (password: admin123)
INSERT INTO admins (username, email, password) VALUES ('admin', 'admin@example.com', '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi');

CREATE TABLE products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    old_price DECIMAL(10,2),
    current_price DECIMAL(10,2) NOT NULL,
    image_url VARCHAR(255),
    active TINYINT(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE purchase_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    gender VARCHAR(20),
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE purchase_request_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    request_id INT,
    product_id INT,
    product_name VARCHAR(100),
    quantity INT,
    price DECIMAL(10,2),
    FOREIGN KEY (request_id) REFERENCES purchase_requests(id) ON DELETE CASCADE
);
"""

user_website_index_html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Ramesh Smart Shop</title>
    <style>
        :root { --primary: #0066cc; --bg: #f5f5f5; --card: #ffffff; --text: #333333; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 0; background: var(--bg); color: var(--text); }
        .container { max-width: 800px; margin: 0 auto; padding: 16px; }
        header { background: var(--card); padding: 16px; text-align: center; box-shadow: 0 2px 4px rgba(0,0,0,0.1); position: sticky; top: 0; z-index: 100; display: flex; justify-content: space-between; align-items: center; }
        h1 { margin: 0; font-size: 20px; color: var(--primary); }
        .cart-btn { background: var(--primary); color: white; border: none; padding: 8px 16px; border-radius: 20px; font-weight: bold; cursor: pointer; }
        
        .welcome-screen { text-align: center; margin-top: 50px; background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.05); }
        input, select, button { width: 100%; padding: 12px; margin: 8px 0; border: 1px solid #ddd; border-radius: 8px; box-sizing: border-box; font-size: 16px; }
        .btn-primary { background: var(--primary); color: white; border: none; cursor: pointer; font-weight: bold; }
        
        .products-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 16px; margin-top: 16px; }
        .product-card { background: var(--card); border-radius: 12px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); display: flex; flex-direction: column; }
        .product-image { width: 100%; height: 150px; object-fit: cover; background: #eee; }
        .product-info { padding: 12px; flex-grow: 1; display: flex; flex-direction: column; }
        .product-name { font-weight: bold; margin: 0 0 4px 0; font-size: 14px; }
        .product-category { color: #666; font-size: 12px; margin: 0 0 8px 0; }
        .price-row { display: flex; align-items: baseline; gap: 8px; margin-bottom: 12px; }
        .current-price { font-weight: bold; color: #e91e63; }
        .old-price { text-decoration: line-through; color: #999; font-size: 12px; }
        .add-to-cart { background: #e0f7fa; color: #006064; border: none; padding: 8px; border-radius: 4px; font-weight: bold; cursor: pointer; margin-top: auto; }
        
        .cart-modal { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 200; align-items: flex-end; justify-content: center; }
        .cart-content { background: white; width: 100%; max-width: 800px; max-height: 80vh; overflow-y: auto; border-radius: 20px 20px 0 0; padding: 20px; box-sizing: border-box; }
        .cart-item { display: flex; justify-content: space-between; align-items: center; padding: 12px 0; border-bottom: 1px solid #eee; }
        .qty-controls { display: flex; align-items: center; gap: 12px; }
        .qty-btn { width: 30px; height: 30px; border-radius: 15px; border: 1px solid #ddd; background: white; cursor: pointer; display: flex; align-items: center; justify-content: center; font-weight: bold; }
        .cart-total { display: flex; justify-content: space-between; font-weight: bold; font-size: 18px; margin: 20px 0; }
        
        .close-modal { background: none; border: none; font-size: 24px; position: absolute; right: 20px; top: 10px; cursor: pointer; }
    </style>
</head>
<body>

<div id="app">
    <!-- Welcome Screen -->
    <div id="welcome-screen" class="container">
        <div class="welcome-screen">
            <h2>Welcome to Ramesh Smart Shop</h2>
            <p>Please enter your details to continue</p>
            <input type="text" id="user-name" placeholder="Your Name" required>
            <select id="user-gender">
                <option value="Male">Male</option>
                <option value="Female">Female</option>
                <option value="Other">Other</option>
                <option value="Prefer not to say">Prefer not to say</option>
            </select>
            <button class="btn-primary" onclick="startShopping()">CONTINUE SHOPPING</button>
        </div>
    </div>

    <!-- Shop Screen -->
    <div id="shop-screen" style="display: none;">
        <header>
            <h1>Ramesh Smart Shop</h1>
            <button class="cart-btn" onclick="openCart()">Cart (<span id="cart-count">0</span>)</button>
        </header>
        <div class="container">
            <input type="text" id="search-bar" placeholder="Search products..." onkeyup="filterProducts()">
            <div id="products-grid" class="products-grid">
                <!-- Products loaded here -->
            </div>
        </div>
    </div>

    <!-- Cart Modal -->
    <div id="cart-modal" class="cart-modal">
        <div class="cart-content">
            <button class="close-modal" onclick="closeCart()">&times;</button>
            <h2>Your Cart</h2>
            <div id="cart-items">
                <!-- Cart items here -->
            </div>
            <div class="cart-total">
                <span>Total:</span>
                <span id="cart-total-price">₹0</span>
            </div>
            <button class="btn-primary" onclick="sendToAdmin()">SEND TO ADMIN</button>
        </div>
    </div>
</div>

<script>
    // Configuration
    // In production, you would point this to your actual backend domain
    const API_BASE = '/backend/api'; 
    let products = [];
    let cart = [];
    let user = { name: '', gender: '' };

    // Check if session exists
    if (localStorage.getItem('ramesh_user')) {
        user = JSON.parse(localStorage.getItem('ramesh_user'));
        showShop();
    }

    function startShopping() {
        const name = document.getElementById('user-name').value;
        const gender = document.getElementById('user-gender').value;
        if (name.trim()) {
            user = { name, gender };
            localStorage.setItem('ramesh_user', JSON.stringify(user));
            showShop();
        } else {
            alert('Please enter your name');
        }
    }

    function showShop() {
        document.getElementById('welcome-screen').style.display = 'none';
        document.getElementById('shop-screen').style.display = 'block';
        loadProducts();
    }

    async function loadProducts() {
        try {
            const response = await fetch(`${API_BASE}/products/index.php`);
            const data = await response.json();
            if (data.success) {
                products = data.data.filter(p => p.active == 1);
                renderProducts(products);
            }
        } catch (error) {
            console.error('Error loading products', error);
            document.getElementById('products-grid').innerHTML = '<p>Error loading products. Please try again.</p>';
        }
    }

    function renderProducts(items) {
        const grid = document.getElementById('products-grid');
        grid.innerHTML = items.map(p => `
            <div class="product-card">
                <img src="${p.image_url || 'https://via.placeholder.com/150'}" class="product-image" alt="${p.product_name}">
                <div class="product-info">
                    <h3 class="product-name">${p.product_name}</h3>
                    <span class="product-category">${p.category}</span>
                    <div class="price-row">
                        <span class="current-price">₹${p.current_price}</span>
                        ${p.old_price && p.old_price > p.current_price ? `<span class="old-price">₹${p.old_price}</span>` : ''}
                    </div>
                    <button class="add-to-cart" onclick="addToCart(${p.id})">ADD TO CART</button>
                </div>
            </div>
        `).join('');
    }

    function filterProducts() {
        const query = document.getElementById('search-bar').value.toLowerCase();
        const filtered = products.filter(p => 
            p.product_name.toLowerCase().includes(query) || 
            p.category.toLowerCase().includes(query)
        );
        renderProducts(filtered);
    }

    function addToCart(productId) {
        const product = products.find(p => p.id == productId);
        const existing = cart.find(item => item.product_id == productId);
        
        if (existing) {
            existing.quantity++;
        } else {
            cart.push({
                product_id: product.id,
                product_name: product.product_name,
                price: product.current_price,
                quantity: 1
            });
        }
        updateCartCount();
        alert('Added to cart!');
    }

    function updateCartCount() {
        const count = cart.reduce((sum, item) => sum + item.quantity, 0);
        document.getElementById('cart-count').innerText = count;
    }

    function openCart() {
        renderCartItems();
        document.getElementById('cart-modal').style.display = 'flex';
    }

    function closeCart() {
        document.getElementById('cart-modal').style.display = 'none';
    }

    function updateQuantity(productId, delta) {
        const item = cart.find(i => i.product_id == productId);
        if (item) {
            item.quantity += delta;
            if (item.quantity <= 0) {
                cart = cart.filter(i => i.product_id != productId);
            }
            renderCartItems();
            updateCartCount();
        }
    }

    function renderCartItems() {
        const container = document.getElementById('cart-items');
        if (cart.length === 0) {
            container.innerHTML = '<p>Your cart is empty.</p>';
        } else {
            container.innerHTML = cart.map(item => `
                <div class="cart-item">
                    <div>
                        <strong>${item.product_name}</strong><br>
                        ₹${item.price} x ${item.quantity}
                    </div>
                    <div class="qty-controls">
                        <button class="qty-btn" onclick="updateQuantity(${item.product_id}, -1)">-</button>
                        <span>${item.quantity}</span>
                        <button class="qty-btn" onclick="updateQuantity(${item.product_id}, 1)">+</button>
                    </div>
                </div>
            `).join('');
        }
        
        const total = cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
        document.getElementById('cart-total-price').innerText = `₹${total}`;
    }

    async function sendToAdmin() {
        if (cart.length === 0) {
            alert('Your cart is empty');
            return;
        }
        
        const total = cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
        
        const requestData = {
            customer_name: user.name,
            gender: user.gender,
            total_amount: total,
            items: cart
        };
        
        try {
            const response = await fetch(`${API_BASE}/requests/index.php`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });
            const data = await response.json();
            
            if (data.success) {
                alert('Request sent to admin successfully! Please go to the counter to collect your items and pay.');
                cart = [];
                updateCartCount();
                closeCart();
            } else {
                alert('Failed to send request: ' + data.message);
            }
        } catch (error) {
            alert('Error sending request. Please check your connection.');
        }
    }
</script>
</body>
</html>
"""

write_file('backend/config/database.php', backend_config_database_php)
write_file('backend/api/admin/login.php', backend_api_admin_login_php)
write_file('backend/api/admin/dashboard.php', backend_api_admin_dashboard_php)
write_file('backend/api/products/index.php', backend_api_products_index_php)
write_file('backend/api/requests/index.php', backend_api_requests_index_php)
write_file('backend/api/purchases/index.php', backend_api_purchases_index_php)
write_file('backend/api/upload/index.php', backend_api_upload_index_php)
write_file('backend/database.sql', backend_database_sql)
write_file('website/index.html', user_website_index_html)
print("Files generated")
