<?php
require_once '../../config/database.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    sendJson(false, "Method not allowed", null, 405);
}

$adminId = verifyAdmin($conn);
$stmt = $conn->prepare("SELECT id, username, email, created_at FROM admins WHERE id = ?");
$stmt->execute([$adminId]);
$admin = $stmt->fetch();
sendJson(true, "Profile loaded", $admin);
