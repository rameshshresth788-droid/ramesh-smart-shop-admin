<?php
/**
 * Shared image normalization helper.
 *
 * Extracted from backend/api/upload/index.php so any endpoint that accepts a
 * photo upload (product images, staff registration photos, etc.) can reuse
 * the exact same processing pipeline instead of duplicating it.
 *
 * Behavior is unchanged from the original inline version in upload/index.php.
 */

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

    // Flatten transparency onto white (PNG/WEBP) for a clean look.
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

/**
 * Validates an uploaded $_FILES entry as a real, reasonably-sized image and
 * returns its detected mime type, or sends a JSON error and exits.
 * Shared by upload/index.php and staff/register.php so validation rules
 * (allowed types, size limits, dimension checks) never drift apart.
 */
function validateUploadedImageOrFail($file) {
    if ($file['error'] !== UPLOAD_ERR_OK) {
        sendJson(false, "Upload failed (error code {$file['error']})", null, 400);
    }
    $maxBytes = 8 * 1024 * 1024;
    if ($file['size'] > $maxBytes) {
        sendJson(false, "Image too large. Maximum size is 8 MB.", null, 400);
    }
    if ($file['size'] <= 0) {
        sendJson(false, "Empty file.", null, 400);
    }

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

    return $mime;
}

/**
 * Full pipeline: validate -> normalize -> upload (Cloudinary or local) and
 * return ['url' => ..., 'cloudinary_public_id' => ...|null], or send a JSON
 * error and exit.
 *
 * @param string $uploadsRelativePath On-disk uploads dir relative to the
 *   calling script (e.g. "../../uploads/" from backend/api/upload/, or
 *   "../../../uploads/" from backend/api/admin/staff/).
 * @param string $backendUrlPath The backend root path as seen from the
 *   webserver (e.g. rtrim(dirname(dirname(dirname($_SERVER['SCRIPT_NAME']))), '/')
 *   computed by the caller, since the number of dirname() calls needed
 *   depends on how deep the calling script lives under backend/api/).
 */
function handleImageUploadOrFail($conn, $file, $uploadsRelativePath, $backendUrlPath) {
    $mime = validateUploadedImageOrFail($file);

    $processedPath = tempnam(sys_get_temp_dir(), 'rss_img_');
    if (!processProductImage($file['tmp_name'], $mime, $processedPath)) {
        @unlink($processedPath);
        sendJson(false, "Could not process image.", null, 500);
    }

    if (cloudinaryIsConfigured($conn)) {
        $result = cloudinaryUpload($conn, $processedPath);
        @unlink($processedPath);
        if ($result) {
            return ["url" => $result['url'], "cloudinary_public_id" => $result['public_id']];
        }
        error_log("Cloudinary upload failed, falling back to local storage.");
        // processedPath was already unlinked above; recreate from scratch for local fallback
        $processedPath = tempnam(sys_get_temp_dir(), 'rss_img_');
        if (!processProductImage($file['tmp_name'], $mime, $processedPath)) {
            @unlink($processedPath);
            sendJson(false, "Could not process image.", null, 500);
        }
    }

    $targetDir = $uploadsRelativePath;
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
    $url = "{$scheme}://{$host}{$backendUrlPath}/uploads/{$newName}";

    return ["url" => $url, "cloudinary_public_id" => null];
}
