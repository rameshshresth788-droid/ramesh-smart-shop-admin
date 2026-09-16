<?php
require_once '../../../config/database.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendJson(false, "Method not allowed", null, 405);
}

verifyAdmin($conn);

$id = $_GET['id'] ?? null;
if (!$id) sendJson(false, "Request id is required", null, 400);

$stmt = $conn->prepare("UPDATE purchase_requests SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING'");
$stmt->execute([$id]);

if ($stmt->rowCount() === 0) {
    sendJson(false, "Request not found or already processed", null, 409);
}

sendJson(true, "Request cancelled");
