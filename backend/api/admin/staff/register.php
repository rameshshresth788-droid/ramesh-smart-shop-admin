<?php
/**
 * Staff self-registration.
 *
 * Intentionally NOT behind verifyAdmin()/requireHeadAdmin() - a brand new
 * staff member has no token yet. Every new row is forced to
 * role = STAFF, approval_status = PENDING regardless of any client input,
 * so this endpoint can never be used to create or approve a Head Admin.
 *
 * Accepts multipart/form-data (so the photo can be attached directly):
 *   name, age, gender, username, phone, password, gemini_api_key (optional),
 *   photo (optional file field)
 */
require_once '../../../config/database.php';
require_once '../../../config/cloudinary.php';
require_once '../../../config/image_processing.php';
require_once '../../../config/crypto.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendJson(false, "Method not allowed", null, 405);
}

$name = trim($_POST['name'] ?? '');
$ageRaw = trim($_POST['age'] ?? '');
$gender = trim($_POST['gender'] ?? '');
$username = trim($_POST['username'] ?? '');
$phone = trim($_POST['phone'] ?? '');
$password = (string) ($_POST['password'] ?? '');
$geminiApiKey = trim($_POST['gemini_api_key'] ?? '');

$errors = [];
if ($name === '') $errors[] = "Full name is required";
if ($username === '') $errors[] = "Username is required";
if (!preg_match('/^[a-zA-Z0-9_.]{3,50}$/', $username)) $errors[] = "Username must be 3-50 characters (letters, numbers, _ or .)";
if ($phone === '') $errors[] = "Phone is required";
if (strlen($password) < 6) $errors[] = "Password must be at least 6 characters";
$age = null;
if ($ageRaw !== '') {
    if (!ctype_digit($ageRaw) || (int) $ageRaw < 16 || (int) $ageRaw > 100) {
        $errors[] = "Age must be a valid number between 16 and 100";
    } else {
        $age = (int) $ageRaw;
    }
}
if ($gender !== '' && !in_array($gender, ['Male', 'Female', 'Other'], true)) {
    $errors[] = "Gender must be Male, Female or Other";
}

if (!empty($errors)) {
    sendJson(false, implode(". ", $errors), null, 422);
}

// Username must be unique among ALL admins (head admin + staff).
$stmt = $conn->prepare("SELECT id FROM admins WHERE username = ?");
$stmt->execute([$username]);
if ($stmt->fetch()) {
    sendJson(false, "That username is already taken. Please choose another.", null, 409);
}

// Photo is optional at registration time.
$photoUrl = null;
$cloudinaryPublicId = null;
if (isset($_FILES['photo']) && $_FILES['photo']['error'] !== UPLOAD_ERR_NO_FILE) {
    // backend/api/admin/staff/register.php -> backend root is three levels up
    $backendUrlPath = rtrim(dirname(dirname(dirname(dirname($_SERVER['SCRIPT_NAME'])))), '/');
    $uploadResult = handleImageUploadOrFail($conn, $_FILES['photo'], "../../../uploads/", $backendUrlPath);
    $photoUrl = $uploadResult['url'];
    $cloudinaryPublicId = $uploadResult['cloudinary_public_id'];
}

$passwordHash = password_hash($password, PASSWORD_BCRYPT);
$encryptedGeminiKey = $geminiApiKey !== '' ? encryptSecretValue($geminiApiKey, $CONFIG) : null;

try {
    $stmt = $conn->prepare("
        INSERT INTO admins
            (username, email, password, name, phone, age, gender, photo_url, gemini_api_key, role, approval_status)
        VALUES
            (?, NULL, ?, ?, ?, ?, ?, ?, ?, 'STAFF', 'PENDING')
    ");
    $stmt->execute([$username, $passwordHash, $name, $phone, $age, $gender ?: null, $photoUrl, $encryptedGeminiKey]);
} catch (PDOException $e) {
    // Most likely a duplicate-key race, or the migration hasn't been run yet.
    if (isset($cloudinaryPublicId) && $cloudinaryPublicId) {
        cloudinaryDelete($conn, $cloudinaryPublicId);
    }
    sendJson(false, "Could not create staff account. Make sure the database migration (migrate_staff_v2.sql) has been run, and that the username is unique.", null, 500);
}

sendJson(true, "Registration submitted. A Head Admin must approve your account before you can log in.", [
    "id" => (int) $conn->lastInsertId(),
    "approval_status" => "PENDING"
], 201);
