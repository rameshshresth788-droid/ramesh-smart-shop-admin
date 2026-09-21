<?php
require_once '../../config/database.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendJson(false, "Method not allowed", null, 405);
}

$data = readJsonBody();
$username = trim($data->username ?? '');
$password = (string) ($data->password ?? '');

if ($username === '' || $password === '') {
    sendJson(false, "Username and password are required", null, 422);
}

$stmt = $conn->prepare("
    SELECT id, password, role, approval_status
    FROM admins
    WHERE username = ? OR email = ?
    LIMIT 1
");
$stmt->execute([$username, $username]);
$admin = $stmt->fetch();

if (!$admin || !password_verify($password, $admin['password'])) {
    sendJson(false, "Invalid username or password", null, 401);
}

if ($admin['approval_status'] !== 'APPROVED') {
    $statusMessages = [
        'PENDING' => 'Your staff account is waiting for Head Admin approval.',
        'REJECTED' => 'Your staff registration was rejected.',
        'DISABLED' => 'Your staff account is disabled.',
    ];

    $message = $statusMessages[$admin['approval_status']] ?? 'Your account is not approved.';
    sendJson(false, $message, [
        "approval_status" => $admin['approval_status']
    ], 403);
}

$token = bin2hex(random_bytes(32));
$ttlHours = (int) ($CONFIG['admin_token_ttl_hours'] ?? 72);
$expiresAt = date('Y-m-d H:i:s', time() + $ttlHours * 3600);

$stmt = $conn->prepare("
    UPDATE admins
    SET token = ?, token_expires_at = ?
    WHERE id = ?
");
$stmt->execute([$token, $expiresAt, $admin['id']]);

sendJson(true, "Login successful", [
    "token" => $token,
    "expires_at" => $expiresAt,
    "admin_id" => (int) $admin['id'],
    "role" => $admin['role'],
    "approval_status" => $admin['approval_status']
]);
