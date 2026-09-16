<?php
require_once '../../config/database.php';
verifyAdmin($conn);

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendJson(false, "Method not allowed", null, 405);
}

$data = readJsonBody();
$imageBase64 = $data->image_base64 ?? '';
if (!$imageBase64) {
    sendJson(false, "image_base64 is required", null, 400);
}

$provider = getSetting($conn, 'ai_provider', 'gemini');
$apiKey = getSetting($conn, 'ai_api_key', '');
$model = getSetting($conn, 'ai_model', 'gemini-2.5-flash');
$customEndpoint = getSetting($conn, 'ai_endpoint', '');

if (!$apiKey) {
    sendJson(false, "AI is not configured yet. Go to Settings -> AI and save your API key.", null, 400);
}

if ($provider !== 'gemini') {
    sendJson(false, "AI provider '{$provider}' is not supported yet. Only 'gemini' is currently implemented.", null, 400);
}

$endpoint = $customEndpoint ?: "https://generativelanguage.googleapis.com/v1beta/models/{$model}:generateContent";
$url = $endpoint . (strpos($endpoint, '?') !== false ? '&' : '?') . 'key=' . urlencode($apiKey);

$prompt = 'Analyze this product photo for a small retail shop. Respond with ONLY a raw JSON object ' .
    '(no markdown, no code fences) with exactly these keys: product_name (string), category (string), ' .
    'product_type (string, more specific than category), description (string, one short sentence), ' .
    'confidence (string, one of "high", "medium", "low"). If you cannot identify the product, ' .
    'use your best guess and set confidence to "low".';

$payload = [
    'contents' => [[
        'parts' => [
            ['text' => $prompt],
            ['inline_data' => ['mime_type' => 'image/jpeg', 'data' => $imageBase64]],
        ],
    ]],
    'generationConfig' => ['temperature' => 0.2],
];

$ch = curl_init();
curl_setopt($ch, CURLOPT_URL, $url);
curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_HTTPHEADER, ['Content-Type: application/json']);
curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($payload));
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_TIMEOUT, 45);
$response = curl_exec($ch);
$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
$curlError = curl_error($ch);
curl_close($ch);

if ($curlError) {
    sendJson(false, "Could not reach AI service: {$curlError}", null, 502);
}
if ($httpCode >= 300) {
    error_log("AI analyze failed: HTTP {$httpCode} {$response}");
    sendJson(false, "AI service returned an error (HTTP {$httpCode}). Check your AI API key and model name in Settings.", null, 502);
}

$body = json_decode($response, true);
$text = $body['candidates'][0]['content']['parts'][0]['text'] ?? '';
if (!$text) {
    sendJson(false, "AI service returned an empty response.", null, 502);
}

// Strip markdown code fences if the model added them despite instructions.
$clean = trim($text);
$clean = preg_replace('/^```json\s*/i', '', $clean);
$clean = preg_replace('/^```\s*/', '', $clean);
$clean = preg_replace('/```\s*$/', '', $clean);
$clean = trim($clean);

$parsed = json_decode($clean, true);
if (!is_array($parsed)) {
    // Gracefully degrade instead of crashing - admin can still fill fields manually.
    sendJson(true, "AI returned an unreadable response. Please fill in the details manually.", [
        "product_name" => "",
        "category" => "",
        "product_type" => "",
        "description" => "",
        "confidence" => "low",
        "ai_raw_failure" => true,
    ]);
}

$result = [
    "product_name" => (string)($parsed['product_name'] ?? ''),
    "category" => (string)($parsed['category'] ?? ''),
    "product_type" => (string)($parsed['product_type'] ?? ''),
    "description" => (string)($parsed['description'] ?? ''),
    "confidence" => (string)($parsed['confidence'] ?? 'low'),
];

sendJson(true, "AI analysis complete", $result);
