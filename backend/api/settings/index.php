<?php
require_once '../../config/database.php';
verifyAdmin($conn);

$method = $_SERVER['REQUEST_METHOD'];

$knownKeys = [
    'shop_name', 'currency_symbol', 'purchase_history_hours', 'request_expiry_minutes',
    'ai_provider', 'ai_api_key', 'ai_model', 'ai_endpoint',
    'cloudinary_cloud_name', 'cloudinary_api_key', 'cloudinary_api_secret',
    'website_origin',
];
$numericKeys = ['purchase_history_hours', 'request_expiry_minutes'];

if ($method == 'GET') {
    sendJson(true, "Settings loaded", getAllSettingsForAdmin($conn));

} elseif ($method == 'POST') {
    $data = readJsonBody();
    $data = (array) $data;

    foreach ($data as $key => $value) {
        if (!in_array($key, $knownKeys, true)) {
            continue; // ignore unknown keys instead of failing the whole save
        }
        if (in_array($key, $numericKeys, true) && (!is_numeric($value) || $value < 0)) {
            sendJson(false, "{$key} must be a non-negative number", null, 422);
        }
        // Don't overwrite a secret with an empty string sent by a form that
        // didn't touch that field (client sends "" for "leave unchanged").
        if (in_array($key, ['ai_api_key', 'cloudinary_api_secret', 'cloudinary_api_key'], true) && $value === '') {
            continue;
        }
        saveSetting($conn, $key, (string) $value);
    }

    sendJson(true, "Settings updated", getAllSettingsForAdmin($conn));

} else {
    sendJson(false, "Method not allowed", null, 405);
}
