<?php
require_once '../../config/database.php';

$method = $_SERVER['REQUEST_METHOD'];

if ($method !== 'POST') {
    sendJson(false, "Method not allowed", null, 405);
}

cleanupExpiredData($conn);

$data = readJsonBody();
$customerName = trim($data->customer_name ?? '');
$gender = trim($data->gender ?? '');
$sessionId = trim($data->session_id ?? '');
$items = $data->items ?? [];

if ($customerName === '') {
    sendJson(false, "Name is required", null, 422);
}
if (!is_array($items) || count($items) === 0) {
    sendJson(false, "Cart is empty", null, 422);
}

// ---- Idempotency: header first, JSON body field as fallback ----
$headers = function_exists('apache_request_headers') ? apache_request_headers() : [];
$idempotencyKey = $headers['X-Idempotency-Key'] ?? ($_SERVER['HTTP_X_IDEMPOTENCY_KEY'] ?? ($data->idempotency_key ?? ''));
$idempotencyKey = trim($idempotencyKey);

if ($idempotencyKey !== '') {
    $stmt = $conn->prepare("SELECT id, total_amount, status FROM purchase_requests WHERE idempotency_key = ?");
    $stmt->execute([$idempotencyKey]);
    $existing = $stmt->fetch();
    if ($existing) {
        // Duplicate submit (e.g. double tap) - return the original request instead of creating a new one.
        sendJson(true, "Request already sent to admin", [
            "request_id" => (int) $existing['id'],
            "total" => (float) $existing['total_amount'],
            "status" => $existing['status'],
        ]);
    }
}

// ---- Validate items and price everything from the DB. Never trust client price/total. ----
$lineItems = [];
$total = 0.0;

foreach ($items as $item) {
    $productId = (int) ($item->product_id ?? 0);
    $qty = (int) ($item->quantity ?? 0);
    if ($productId <= 0 || $qty <= 0 || $qty > 999) {
        sendJson(false, "Invalid item in cart", null, 422);
    }

    $stmt = $conn->prepare("SELECT id, product_name, current_price, image_url, status FROM products WHERE id = ?");
    $stmt->execute([$productId]);
    $product = $stmt->fetch();
    if (!$product || $product['status'] !== 'ACTIVE') {
        sendJson(false, "One of the items in your cart is no longer available. Please refresh and try again.", null, 422);
    }

    $unitPrice = (float) $product['current_price'];
    $subtotal = round($unitPrice * $qty, 2);
    $total += $subtotal;

    $lineItems[] = [
        'product_id' => $product['id'],
        'product_name' => $product['product_name'],
        'image_url' => $product['image_url'],
        'quantity' => $qty,
        'unit_price' => $unitPrice,
        'subtotal' => $subtotal,
    ];
}
$total = round($total, 2);

try {
    $conn->beginTransaction();

    $stmt = $conn->prepare(
        "INSERT INTO purchase_requests (session_id, customer_name, gender, total_amount, status, idempotency_key) VALUES (?, ?, ?, ?, 'PENDING', ?)"
    );
    $stmt->execute([$sessionId ?: null, $customerName, $gender ?: null, $total, $idempotencyKey ?: null]);
    $requestId = (int) $conn->lastInsertId();

    $itemStmt = $conn->prepare(
        "INSERT INTO purchase_request_items (request_id, product_id, product_name, image_url, quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?, ?, ?)"
    );
    foreach ($lineItems as $li) {
        $itemStmt->execute([$requestId, $li['product_id'], $li['product_name'], $li['image_url'], $li['quantity'], $li['unit_price'], $li['subtotal']]);
    }

    $conn->commit();
} catch (Exception $e) {
    $conn->rollBack();
    sendJson(false, "Could not send your request. Please try again.", null, 500);
}

sendJson(true, "Request sent to admin successfully", ["request_id" => $requestId, "total" => $total, "status" => "PENDING"]);
