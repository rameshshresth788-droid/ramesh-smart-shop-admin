<?php
/**
 * Minimal at-rest encryption for sensitive values stored in the database,
 * currently used for each staff member's personal Gemini API key
 * (admins.gemini_api_key).
 *
 * Uses AES-256-CBC with a key derived from $CONFIG['app_secret'].
 *
 * For strongest protection, add to backend/config/config.php:
 *   'app_secret' => 'some-long-random-string-you-generate-once',
 * (e.g. via: php -r "echo bin2hex(random_bytes(32));")
 *
 * If 'app_secret' is not set, a deterministic fallback derived from the
 * database name is used instead so existing deployments don't break on
 * upgrade - but this is weaker than a real secret, so setting app_secret
 * manually is strongly recommended before relying on this in production.
 */

function rssEncryptionKey($CONFIG) {
    $secret = $CONFIG['app_secret'] ?? null;
    if (!$secret) {
        $secret = 'rss_fallback_' . ($CONFIG['db_name'] ?? 'ramesh_smart_shop');
    }
    return hash('sha256', $secret, true); // 32 raw bytes for AES-256
}

/**
 * Encrypts a plaintext secret for storage. Returns null for empty input
 * (so "leave blank to keep unchanged" semantics stay simple upstream).
 */
function encryptSecretValue($plainText, $CONFIG) {
    if ($plainText === null || $plainText === '') return null;
    $key = rssEncryptionKey($CONFIG);
    $iv = random_bytes(16);
    $cipherText = openssl_encrypt($plainText, 'aes-256-cbc', $key, OPENSSL_RAW_DATA, $iv);
    if ($cipherText === false) return null;
    return base64_encode($iv . $cipherText);
}

/**
 * Decrypts a value previously produced by encryptSecretValue(). Returns
 * null on any failure (corrupt data, wrong key, empty input) rather than
 * throwing, since callers treat "no key available" as a normal case.
 */
function decryptSecretValue($stored, $CONFIG) {
    if (!$stored) return null;
    $raw = base64_decode($stored, true);
    if ($raw === false || strlen($raw) < 17) return null;
    $iv = substr($raw, 0, 16);
    $cipherText = substr($raw, 16);
    $key = rssEncryptionKey($CONFIG);
    $plainText = openssl_decrypt($cipherText, 'aes-256-cbc', $key, OPENSSL_RAW_DATA, $iv);
    return $plainText === false ? null : $plainText;
}
