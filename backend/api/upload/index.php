<?php
require_once '../../config/database.php';
require_once '../../config/cloudinary.php';
require_once '../../config/image_processing.php';
verifyAdmin($conn);

if ($_SERVER['REQUEST_METHOD'] !== 'POST' || !isset($_FILES['image'])) {
    sendJson(false, "No image file uploaded (expected multipart field 'image')", null, 400);
}

$file = $_FILES['image'];

// backend/api/upload/index.php -> backend root is two levels up from here
$backendUrlPath = rtrim(dirname(dirname(dirname($_SERVER['SCRIPT_NAME']))), '/');
$result = handleImageUploadOrFail($conn, $file, "../../uploads/", $backendUrlPath);

sendJson(true, "Image uploaded", ["url" => $result['url'], "cloudinary_public_id" => $result['cloudinary_public_id']]);
