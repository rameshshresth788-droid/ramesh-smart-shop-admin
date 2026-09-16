<?php
require_once '../../../config/database.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    sendJson(false, "Method not allowed", null, 405);
}

verifyAdmin($conn);
cleanupExpiredData($conn);

$id = $_GET['id'] ?? null;

if ($id) {
    $stmt = $conn->prepare("SELECT * FROM purchase_requests WHERE id = ?");
    $stmt->execute([$id]);
    $request = $stmt->fetch();
    if (!$request) sendJson(false, "Request not found", null, 404);

    $stmt = $conn->prepare("SELECT * FROM purchase_request_items WHERE request_id = ?");
    $stmt->execute([$id]);
    $request['items'] = $stmt->fetchAll();
    sendJson(true, "Request loaded", $request);
}

$status = $_GET['status'] ?? 'PENDING';
if (!in_array($status, ['PENDING', 'COMPLETED', 'CANCELLED', 'EXPIRED'], true)) {
    $status = 'PENDING';
}

$stmt = $conn->prepare("SELECT * FROM purchase_requests WHERE status = ? ORDER BY created_at DESC");
$stmt->execute([$status]);
$requests = $stmt->fetchAll();

$itemStmt = $conn->prepare("SELECT * FROM purchase_request_items WHERE request_id = ?");
foreach ($requests as &$req) {
    $itemStmt->execute([$req['id']]);
    $req['items'] = $itemStmt->fetchAll();
}

sendJson(true, "Requests loaded", $requests);
