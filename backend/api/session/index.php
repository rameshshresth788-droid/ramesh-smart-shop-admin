<?php
require_once '../../config/database.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendJson(false, "Method not allowed", null, 405);
}

$data = readJsonBody();
$name = trim($data->name ?? '');
$gender = trim($data->gender ?? '');

if ($name === '' || mb_strlen($name) > 100) {
    sendJson(false, "Please enter a valid name", null, 422);
}
$allowedGenders = ['Male', 'Female', 'Other', 'Prefer not to say', ''];
if (!in_array($gender, $allowedGenders, true)) {
    $gender = '';
}

$sessionId = bin2hex(random_bytes(20));
$stmt = $conn->prepare("INSERT INTO users (session_id, name, gender) VALUES (?, ?, ?)");
$stmt->execute([$sessionId, $name, $gender ?: null]);

sendJson(true, "Session created", ["session_id" => $sessionId, "name" => $name, "gender" => $gender]);
