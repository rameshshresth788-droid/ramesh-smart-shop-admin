<?php
require_once '../../config/database.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendJson(false, "Method not allowed", null, 405);
}

$adminId = verifyAdmin($conn);
$stmt = $conn->prepare("UPDATE admins SET token = NULL, token_expires_at = NULL WHERE id = ?");
$stmt->execute([$adminId]);
sendJson(true, "Logged out");
