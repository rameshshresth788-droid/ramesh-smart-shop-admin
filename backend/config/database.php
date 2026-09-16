<?php
// backend/config/database.php
// Core bootstrap included by every API endpoint.

$configFile = __DIR__ . '/config.php';
if (!file_exists($configFile)) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Server not configured: copy backend/config/config.example.php to backend/config/config.php"]);
    exit();
}
$CONFIG = require $configFile;

// ---------------- CORS ----------------
$allowedOrigin = $CONFIG['allowed_origin'] ?? '*';
$requestOrigin = $_SERVER['HTTP_ORIGIN'] ?? '';
if ($allowedOrigin === '*') {
    header("Access-Control-Allow-Origin: *");
} elseif ($requestOrigin !== '' && $requestOrigin === $allowedOrigin) {
    header("Access-Control-Allow-Origin: " . $allowedOrigin);
    header("Vary: Origin");
}
header("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With, X-Idempotency-Key, X-Session-Id");
header("Content-Type: application/json; charset=UTF-8");

if ($_SERVER['REQUEST_METHOD'] == 'OPTIONS') {
    http_response_code(200);
    exit();
}

// ---------------- DB connection ----------------
try {
    $conn = new PDO(
        "mysql:host={$CONFIG['db_host']};dbname={$CONFIG['db_name']};charset=utf8mb4",
        $CONFIG['db_user'],
        $CONFIG['db_password']
    );
    $conn->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $conn->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Database connection error. Check backend/config/config.php."]);
    exit();
}

// ---------------- JSON helpers ----------------
function sendJson($success, $message, $data = null, $httpCode = null) {
    if ($httpCode === null) {
        $httpCode = $success ? 200 : 400;
    }
    http_response_code($httpCode);
    echo json_encode(["success" => $success, "message" => $message, "data" => $data]);
    exit();
}

function readJsonBody() {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw);
    if ($data === null && trim($raw) !== '') {
        sendJson(false, "Invalid JSON in request body", null, 400);
    }
    return $data ?: new stdClass();
}

// ---------------- Admin auth ----------------
function getBearerToken() {
    $headers = function_exists('apache_request_headers') ? apache_request_headers() : [];
    $auth = $headers['Authorization'] ?? ($_SERVER['HTTP_AUTHORIZATION'] ?? '');
    if (!$auth) return '';
    return trim(str_replace('Bearer', '', $auth));
}

// Returns the admin id, or sends a 401 JSON response and exits if not authorized.
function verifyAdmin($conn) {
    $token = getBearerToken();
    if (!$token) {
        sendJson(false, "Unauthorized: No token provided", null, 401);
    }
    $stmt = $conn->prepare("SELECT id, token_expires_at FROM admins WHERE token = ?");
    $stmt->execute([$token]);
    $admin = $stmt->fetch();
    if (!$admin) {
        sendJson(false, "Unauthorized: Invalid token", null, 401);
    }
    if ($admin['token_expires_at'] !== null && strtotime($admin['token_expires_at']) < time()) {
        sendJson(false, "Session expired, please login again", null, 401);
    }
    return $admin['id'];
}

// ---------------- Settings helpers ----------------
// Keys never returned to any client (admin app included) once set - write-only.
function secretSettingKeys() {
    return ['ai_api_key', 'cloudinary_api_secret', 'cloudinary_api_key'];
}

function getSetting($conn, $key, $default = null) {
    $stmt = $conn->prepare("SELECT setting_value FROM settings WHERE setting_key = ?");
    $stmt->execute([$key]);
    $row = $stmt->fetch();
    return $row ? $row['setting_value'] : $default;
}

function getAllSettingsForAdmin($conn) {
    $stmt = $conn->query("SELECT setting_key, setting_value FROM settings");
    $settings = [];
    $secrets = secretSettingKeys();
    while ($row = $stmt->fetch()) {
        if (in_array($row['setting_key'], $secrets, true)) {
            // Never echo the secret back - just tell the admin app whether it's set.
            $settings[$row['setting_key'] . '_is_set'] = ($row['setting_value'] !== '' && $row['setting_value'] !== null);
        } else {
            $settings[$row['setting_key']] = $row['setting_value'];
        }
    }
    return $settings;
}

function saveSetting($conn, $key, $value) {
    $stmt = $conn->prepare("INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE setting_value = ?");
    $stmt->execute([$key, $value, $value]);
}

// ---------------- Cleanup (called opportunistically - see README for cron option) ----------------
function cleanupExpiredData($conn) {
    $historyHours = (float) getSetting($conn, 'purchase_history_hours', 24);
    $expiryMinutes = (int) getSetting($conn, 'request_expiry_minutes', 120);

    // Expire abandoned pending requests.
    $stmt = $conn->prepare("UPDATE purchase_requests SET status = 'EXPIRED' WHERE status = 'PENDING' AND created_at < (NOW() - INTERVAL ? MINUTE)");
    $stmt->execute([$expiryMinutes]);

    // Remove completed purchases older than the configured history window (server time).
    $stmt = $conn->prepare("DELETE FROM purchases WHERE completed_at < (NOW() - INTERVAL ? HOUR)");
    $stmt->execute([$historyHours]);
}
