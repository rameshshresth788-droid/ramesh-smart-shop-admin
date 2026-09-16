<?php
require_once '../../config/database.php';
verifyAdmin($conn);

$method = $_SERVER['REQUEST_METHOD'];

// Server-side cleanup on every relevant hit - see README for an optional real cron job.
cleanupExpiredData($conn);

if ($method == 'GET') {
    $stmt = $conn->query("SELECT * FROM purchases ORDER BY completed_at DESC");
    $purchases = $stmt->fetchAll();

    $itemStmt = $conn->prepare("SELECT * FROM purchase_items WHERE purchase_id = ?");
    foreach ($purchases as &$purchase) {
        $itemStmt->execute([$purchase['id']]);
        $purchase['items'] = $itemStmt->fetchAll();
    }
    sendJson(true, "Purchases loaded", $purchases);

} elseif ($method == 'DELETE') {
    $id = $_GET['id'] ?? null;
    if (!$id) sendJson(false, "Purchase id is required", null, 400);
    $stmt = $conn->prepare("DELETE FROM purchases WHERE id = ?");
    $stmt->execute([$id]);
    if ($stmt->rowCount() === 0) sendJson(false, "Purchase not found", null, 404);
    sendJson(true, "Purchase record deleted");

} else {
    sendJson(false, "Method not allowed", null, 405);
}
