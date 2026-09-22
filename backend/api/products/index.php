<?php
require_once '../../config/database.php';
require_once '../../config/cloudinary.php';

$method = $_SERVER['REQUEST_METHOD'];

function isAdminRequest($conn) {
    $token = getBearerToken();
    if (!$token) return false;
    $stmt = $conn->prepare("SELECT id, token_expires_at FROM admins WHERE token = ?");
    $stmt->execute([$token]);
    $admin = $stmt->fetch();
    if (!$admin) return false;
    if ($admin['token_expires_at'] !== null && strtotime($admin['token_expires_at']) < time()) return false;
    return true;
}

if ($method == 'GET') {
    $id = $_GET['id'] ?? null;
    $admin = isAdminRequest($conn);

    if ($id) {
        $stmt = $conn->prepare("SELECT * FROM products WHERE id = ?");
        $stmt->execute([$id]);
        $product = $stmt->fetch();
        if (!$product) sendJson(false, "Product not found", null, 404);
        if (!$admin && $product['status'] !== 'ACTIVE') sendJson(false, "Product not found", null, 404);
        sendJson(true, "Product loaded", $product);
    }

    // List with optional search/category filter (used by the user website).
    $search = trim($_GET['search'] ?? '');
    $category = trim($_GET['category'] ?? '');

    $sql = "SELECT * FROM products WHERE 1=1";
    $params = [];
    if (!$admin) {
        $sql .= " AND status = 'ACTIVE'";
    } elseif (isset($_GET['status']) && in_array($_GET['status'], ['ACTIVE', 'INACTIVE'], true)) {
        $sql .= " AND status = ?";
        $params[] = $_GET['status'];
    }
    if ($search !== '') {
        $sql .= " AND (product_name LIKE ? OR category LIKE ? OR product_type LIKE ?)";
        $like = "%{$search}%";
        $params[] = $like; $params[] = $like; $params[] = $like;
    }
    if ($category !== '') {
        $sql .= " AND category = ?";
        $params[] = $category;
    }
    $sql .= " ORDER BY id DESC";

    $stmt = $conn->prepare($sql);
    $stmt->execute($params);
    sendJson(true, "Products loaded", $stmt->fetchAll());

} elseif ($method == 'POST') {
    verifyAdmin($conn);
    $data = readJsonBody();

    $name = trim($data->product_name ?? '');
    $category = trim($data->category ?? '');
    $price = $data->current_price ?? null;

    if ($name === '' || $category === '' || $price === null || !is_numeric($price) || $price < 0) {
        sendJson(false, "product_name, category and a valid current_price are required", null, 422);
    }

    $status = ($data->status ?? 'ACTIVE') === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE';

    $stmt = $conn->prepare(
        "INSERT INTO products (product_name, category, product_type, description, current_price, image_url, cloudinary_public_id, status)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
    );
    $stmt->execute([
        $name,
        $category,
        trim($data->product_type ?? ''),
        trim($data->description ?? ''),
        (float) $price,
        $data->image_url ?? null,
        $data->cloudinary_public_id ?? null,
        $status,
    ]);
    sendJson(true, "Product added successfully", ["id" => (int) $conn->lastInsertId()]);

} elseif ($method == 'PUT') {
    verifyAdmin($conn);
    $id = $_GET['id'] ?? null;
    if (!$id) sendJson(false, "Product id is required", null, 400);

    $stmt = $conn->prepare("SELECT * FROM products WHERE id = ?");
    $stmt->execute([$id]);
    $existing = $stmt->fetch();
    if (!$existing) sendJson(false, "Product not found", null, 404);

    $data = readJsonBody();
    $name = trim($data->product_name ?? $existing['product_name']);
    $category = trim($data->category ?? $existing['category']);
    $newPrice = $data->current_price ?? $existing['current_price'];
    if (!is_numeric($newPrice) || $newPrice < 0) {
        sendJson(false, "current_price must be a valid non-negative number", null, 422);
    }
    $status = isset($data->status) && in_array($data->status, ['ACTIVE', 'INACTIVE'], true) ? $data->status : $existing['status'];

    // Track price history: only move current -> old when price actually changes.
    $oldPrice = $existing['old_price'];
    if ((float) $newPrice !== (float) $existing['current_price']) {
        $oldPrice = $existing['current_price'];
    }

    // If image changed and the old image was on Cloudinary, clean up the old one.
    $newImageUrl = $data->image_url ?? $existing['image_url'];
    $newPublicId = $data->cloudinary_public_id ?? $existing['cloudinary_public_id'];
    if ($newImageUrl !== $existing['image_url'] && $existing['cloudinary_public_id']) {
        cloudinaryDelete($conn, $existing['cloudinary_public_id']);
    }

    $stmt = $conn->prepare(
        "UPDATE products SET product_name=?, category=?, product_type=?, description=?, old_price=?, current_price=?, image_url=?, cloudinary_public_id=?, status=? WHERE id=?"
    );
    $stmt->execute([
        $name,
        $category,
        trim($data->product_type ?? $existing['product_type']),
        trim($data->description ?? $existing['description']),
        $oldPrice,
        (float) $newPrice,
        $newImageUrl,
        $newPublicId,
        $status,
        $id,
    ]);
    sendJson(true, "Product updated");

} elseif ($method == 'DELETE') {
    verifyAdmin($conn);
    $id = $_GET['id'] ?? null;
    if (!$id) sendJson(false, "Product id is required", null, 400);

    $stmt = $conn->prepare("SELECT cloudinary_public_id FROM products WHERE id = ?");
    $stmt->execute([$id]);
    $product = $stmt->fetch();
    if (!$product) sendJson(false, "Product not found", null, 404);

    $stmt = $conn->prepare("DELETE FROM products WHERE id = ?");
    $stmt->execute([$id]);

    if ($product['cloudinary_public_id']) {
        cloudinaryDelete($conn, $product['cloudinary_public_id']);
    }
    sendJson(true, "Product deleted");

} else {
    sendJson(false, "Method not allowed", null, 405);
}
