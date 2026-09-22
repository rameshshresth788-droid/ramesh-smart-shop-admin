<?php
require_once '../../../config/database.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendJson(false, "Method not allowed", null, 405);
}

verifyAdmin($conn);

$id = $_GET['id'] ?? null;
if (!$id) sendJson(false, "Request id is required", null, 400);

$stmt = $conn->prepare("SELECT * FROM purchase_requests WHERE id = ?");
$stmt->execute([$id]);
$request = $stmt->fetch();
if (!$request) sendJson(false, "Request not found", null, 404);
if ($request['status'] !== 'PENDING') {
    sendJson(false, "This request is already {$request['status']} and cannot be completed.", null, 409);
}

$stmt = $conn->prepare("SELECT * FROM purchase_request_items WHERE request_id = ?");
$stmt->execute([$id]);
$items = $stmt->fetchAll();

try {
    $conn->beginTransaction();

    $stmt = $conn->prepare("UPDATE purchase_requests SET status = 'COMPLETED' WHERE id = ? AND status = 'PENDING'");
    $stmt->execute([$id]);
    if ($stmt->rowCount() === 0) {
        // Someone else (or a double-tap) already changed it - avoid double-recording the sale.
        $conn->rollBack();
        sendJson(false, "This request was already updated. Please refresh.", null, 409);
    }

    $stmt = $conn->prepare(
        "INSERT INTO purchases (request_id, customer_name, gender, total_amount) VALUES (?, ?, ?, ?)"
    );
    $stmt->execute([$id, $request['customer_name'], $request['gender'], $request['total_amount']]);
    $purchaseId = (int) $conn->lastInsertId();

    $itemStmt = $conn->prepare(
        "INSERT INTO purchase_items (purchase_id, product_id, product_name, image_url, quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?, ?, ?)"
    );
    foreach ($items as $item) {
        $itemStmt->execute([
            $purchaseId,
            $item['product_id'],
            $item['product_name'],
            $item['image_url'],
            $item['quantity'],
            $item['unit_price'],
            $item['subtotal'],
        ]);
    }

    $conn->commit();
} catch (Exception $e) {
    $conn->rollBack();
    sendJson(false, "Could not complete the purchase. Please try again.", null, 500);
}

sendJson(true, "Purchase completed", ["purchase_id" => $purchaseId]);
