<?php
require_once '../../config/database.php';
require_once '../../config/cloudinary.php';
verifyAdmin($conn);

if ($_SERVER['REQUEST_METHOD'] !== 'POST' || !isset($_FILES['image'])) {
    sendJson(false, "No image file uploaded (expected multipart field 'image')", null, 400);
}

$file = $_FILES['image'];

// ---- Basic upload error check ----
if ($file['error'] !== UPLOAD_ERR_OK) {
    sendJson(false, "Upload failed (error code {$file['error']})", null, 400);
}

// ---- Size check (max 8 MB) ----
$maxBytes = 8 * 1024 * 1024;
if ($file['size'] > $maxBytes) {
    sendJson(false, "Image too large. Maximum size is 8 MB.", null, 400);
}
if ($file['size'] <= 0) {
    sendJson(false, "Empty file.", null, 400);
}

// ---- Real MIME type check (don't trust the client's Content-Type) ----
$finfo = finfo_open(FILEINFO_MIME_TYPE);
$mime = finfo_file($finfo, $file['tmp_name']);
finfo_close($finfo);

$allowedMimes = [
    'image/jpeg' => 'jpg',
    'image/png' => 'png',
    'image/webp' => 'webp',
];
if (!isset($allowedMimes[$mime])) {
    sendJson(false, "Invalid file type. Only JPG, PNG or WEBP images are allowed.", null, 400);
}

// ---- Dimension sanity check ----
$imageInfo = @getimagesize($file['tmp_name']);
if ($imageInfo === false) {
    sendJson(false, "File is not a valid image.", null, 400);
}
[$width, $height] = $imageInfo;
if ($width < 50 || $height < 50) {
    sendJson(false, "Image is too small.", null, 400);
}
if ($width > 8000 || $height > 8000) {
    sendJson(false, "Image dimensions are too large.", null, 400);
}

// ---- Normalize: fix orientation, downscale, compress to a web-friendly JPEG ----
$processedPath = tempnam(sys_get_temp_dir(), 'rss_img_');
if (!processProductImage($file['tmp_name'], $mime, $processedPath)) {
    @unlink($processedPath);
    sendJson(false, "Could not process image.", null, 500);
}

// ---- Upload: Cloudinary if configured, else local storage ----
if (cloudinaryIsConfigured($conn)) {
    $result = cloudinaryUpload($conn, $processedPath);
    @unlink($processedPath);
    if ($result) {
        sendJson(true, "Image uploaded", ["url" => $result['url'], "cloudinary_public_id" => $result['public_id']]);
    }
    // Cloudinary configured but the call failed - fall through to local storage
    // so the admin isn't blocked; log this decision.
    error_log("Cloudinary upload failed, falling back to local storage.");
}

$targetDir = "../../uploads/";
if (!file_exists($targetDir)) {
    mkdir($targetDir, 0755, true);
}
$newName = bin2hex(random_bytes(16)) . '.jpg';
$targetFile = $targetDir . $newName;

if (!rename($processedPath, $targetFile)) {
    @unlink($processedPath);
    sendJson(false, "Failed to save image on server.", null, 500);
}
chmod($targetFile, 0644);

$scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$host = $_SERVER['HTTP_HOST'] ?? 'localhost';
// backend/api/upload/index.php -> backend root is two levels up from here
$backendPath = rtrim(dirname(dirname(dirname($_SERVER['SCRIPT_NAME']))), '/');
$url = "{$scheme}://{$host}{$backendPath}/uploads/{$newName}";

sendJson(true, "Image uploaded", ["url" => $url, "cloudinary_public_id" => null]);

/**
 * Reads the source image, fixes EXIF rotation, downsizes if needed
 * (max 1600px on the long edge), and writes a compressed JPEG.
 */
function processProductImage($srcPath, $mime, $destPath) {
    switch ($mime) {
        case 'image/jpeg':
            $img = @imagecreatefromjpeg($srcPath);
            break;
        case 'image/png':
            $img = @imagecreatefrompng($srcPath);
            break;
        case 'image/webp':
            $img = @imagecreatefromwebp($srcPath);
            break;
        default:
            return false;
    }
    if (!$img) return false;

    // Fix orientation using EXIF data (JPEG only).
    if ($mime === 'image/jpeg' && function_exists('exif_read_data')) {
        $exif = @exif_read_data($srcPath);
        if (!empty($exif['Orientation'])) {
            switch ($exif['Orientation']) {
                case 3: $img = imagerotate($img, 180, 0); break;
                case 6: $img = imagerotate($img, -90, 0); break;
                case 8: $img = imagerotate($img, 90, 0); break;
            }
        }
    }

    // Flatten transparency onto white (PNG/WEBP) for a clean e-commerce look.
    $width = imagesx($img);
    $height = imagesy($img);
    $flat = imagecreatetruecolor($width, $height);
    $white = imagecolorallocate($flat, 255, 255, 255);
    imagefill($flat, 0, 0, $white);
    imagealphablending($flat, true);
    imagecopy($flat, $img, 0, 0, 0, 0, $width, $height);
    imagedestroy($img);
    $img = $flat;

    // Downscale to a max dimension while keeping aspect ratio and quality.
    $maxDim = 1600;
    if ($width > $maxDim || $height > $maxDim) {
        $ratio = min($maxDim / $width, $maxDim / $height);
        $newW = (int) round($width * $ratio);
        $newH = (int) round($height * $ratio);
        $resized = imagecreatetruecolor($newW, $newH);
        imagecopyresampled($resized, $img, 0, 0, 0, 0, $newW, $newH, $width, $height);
        imagedestroy($img);
        $img = $resized;
    }

    $ok = imagejpeg($img, $destPath, 85);
    imagedestroy($img);
    return $ok;
}
