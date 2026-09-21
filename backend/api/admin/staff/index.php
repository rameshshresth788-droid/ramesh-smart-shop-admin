<?php
require_once '../../../config/database.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    sendJson(false, "Method not allowed", null, 405);
}

requireHeadAdmin($conn);

$stmt = $conn->prepare("
    SELECT
        id,
        name,
        phone,
        username,
        email,
        role,
        approval_status,
        photo_url,
        age,
        gender,
        created_at,
        updated_at
    FROM admins
    WHERE role = 'STAFF'
    ORDER BY created_at DESC
");
$stmt->execute();

$staff = $stmt->fetchAll();

sendJson(true, "Staff list loaded", $staff);
