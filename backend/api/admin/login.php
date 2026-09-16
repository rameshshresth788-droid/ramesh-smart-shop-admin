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

$stmt = $conn->prepare("SELECT id, password FROM admins WHERE username = ? OR email = ?");
$stmt->execute([$username, $username]);
$admin = $stmt->fetch();

if (!$admin || !password_verify($password, $admin['password'])) {
    // Same generic message either way - don't reveal whether the username exists.
    sendJson(false, "Invalid username or password", null, 401);
}

$token = bin2hex(random_bytes(32));
$ttlHours = (int) ($CONFIG['admin_token_ttl_hours'] ?? 72);
$expiresAt = date('Y-m-d H:i:s', time() + $ttlHours * 3600);

$stmt = $conn->prepare("UPDATE admins SET token = ?, token_expires_at = ? WHERE id = ?");
$stmt->execute([$token, $expiresAt, $admin['id']]);

sendJson(true, "Login successful", ["token" => $token, "expires_at" => $expiresAt]);
