<?php
require_once '../../config/database.php';
verifyAdmin($conn);
cleanupExpiredData($conn);

$data = [];

$stmt = $conn->query("SELECT COUNT(*) as count FROM purchase_requests WHERE status = 'PENDING'");
$data['pending_requests'] = (int) $stmt->fetch()['count'];

$stmt = $conn->query("SELECT COUNT(*) as count FROM purchases WHERE DATE(completed_at) = CURDATE()");
$data['completed_today'] = (int) $stmt->fetch()['count'];

$stmt = $conn->query("SELECT COUNT(*) as count FROM products");
$data['total_products'] = (int) $stmt->fetch()['count'];

$stmt = $conn->query("SELECT COUNT(*) as count FROM products WHERE status = 'ACTIVE'");
$data['active_products'] = (int) $stmt->fetch()['count'];

$stmt = $conn->query("SELECT COALESCE(SUM(total_amount), 0) as total FROM purchases WHERE DATE(completed_at) = CURDATE()");
$data['todays_sales'] = (float) $stmt->fetch()['total'];

sendJson(true, "Dashboard data loaded", $data);
