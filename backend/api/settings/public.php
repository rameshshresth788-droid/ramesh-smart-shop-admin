<?php
require_once '../../config/database.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    sendJson(false, "Method not allowed", null, 405);
}

sendJson(true, "Public settings loaded", [
    "shop_name" => getSetting($conn, 'shop_name', 'Ramesh Smart Shop'),
    "currency_symbol" => getSetting($conn, 'currency_symbol', '₹'),
]);
