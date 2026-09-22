<?php
/**
 * Minimal Cloudinary integration using signed uploads via cURL.
 * No SDK/composer dependency required.
 *
 * Cloud name, API key and API secret are stored in the `settings` table
 * (set from Admin App -> Settings -> Cloudinary). The API secret is
 * write-only: it is never returned by GET /settings, never sent to the
 * Android app's normal responses, and never sent to the website JS.
 * Only this backend file ever reads it.
 */

function cloudinaryIsConfigured($conn) {
    $cloud = getSetting($conn, 'cloudinary_cloud_name', '');
    $key = getSetting($conn, 'cloudinary_api_key', '');
    $secret = getSetting($conn, 'cloudinary_api_secret', '');
    return $cloud && $key && $secret;
}

/**
 * Uploads a local file to Cloudinary.
 * Returns ['url' => ..., 'public_id' => ...] on success, or false on failure.
 */
function cloudinaryUpload($conn, $localFilePath) {
    $cloud = getSetting($conn, 'cloudinary_cloud_name', '');
    $key = getSetting($conn, 'cloudinary_api_key', '');
    $secret = getSetting($conn, 'cloudinary_api_secret', '');
    if (!$cloud || !$key || !$secret) return false;

    $timestamp = time();
    $folder = 'ramesh_smart_shop/products';
    // Params to sign must be sorted alphabetically, secret appended, then sha1.
    $paramsToSign = "folder={$folder}&timestamp={$timestamp}";
    $signature = sha1($paramsToSign . $secret);

    $url = "https://api.cloudinary.com/v1_1/{$cloud}/image/upload";
    $postFields = [
        'file' => new CURLFile($localFilePath),
        'api_key' => $key,
        'timestamp' => $timestamp,
        'signature' => $signature,
        'folder' => $folder,
    ];

    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, $url);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, $postFields);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_TIMEOUT, 30);
    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $curlError = curl_error($ch);
    curl_close($ch);

    if ($curlError || $httpCode >= 300) {
        error_log("Cloudinary upload failed: HTTP $httpCode $curlError $response");
        return false;
    }

    $data = json_decode($response, true);
    if (!isset($data['secure_url']) || !isset($data['public_id'])) {
        return false;
    }
    return ['url' => $data['secure_url'], 'public_id' => $data['public_id']];
}

/**
 * Deletes an image from Cloudinary by its public_id. Best-effort - failures
 * are logged but never block the caller (e.g. product delete should still
 * succeed even if Cloudinary cleanup fails).
 */
function cloudinaryDelete($conn, $publicId) {
    if (!$publicId) return;
    $cloud = getSetting($conn, 'cloudinary_cloud_name', '');
    $key = getSetting($conn, 'cloudinary_api_key', '');
    $secret = getSetting($conn, 'cloudinary_api_secret', '');
    if (!$cloud || !$key || !$secret) return;

    $timestamp = time();
    $paramsToSign = "public_id={$publicId}&timestamp={$timestamp}";
    $signature = sha1($paramsToSign . $secret);

    $url = "https://api.cloudinary.com/v1_1/{$cloud}/image/destroy";
    $postFields = [
        'public_id' => $publicId,
        'api_key' => $key,
        'timestamp' => $timestamp,
        'signature' => $signature,
    ];
    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, $url);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, $postFields);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_TIMEOUT, 15);
    curl_exec($ch);
    if (curl_error($ch)) {
        error_log("Cloudinary delete failed for {$publicId}: " . curl_error($ch));
    }
    curl_close($ch);
}
