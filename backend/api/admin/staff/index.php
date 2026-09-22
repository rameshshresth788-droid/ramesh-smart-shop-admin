<?php
/**
 * Staff management (Head Admin only).
 *
 * GET    ?            -> list all staff
 * GET    ?id=N         -> single staff detail
 * POST   {action,id}   -> action is one of approve|reject|disable|enable
 * PUT    ?id=N  {body} -> edit staff profile (name, phone, age, gender,
 *                         username, gemini_api_key, password - all optional,
 *                         only provided fields are changed)
 * DELETE ?id=N         -> permanently delete a staff account
 *
 * Every mutation here requires requireHeadAdmin($conn), which itself
 * re-derives the caller's role from the database using their auth token -
 * the Android app's claimed role is never trusted. A target account must
 * always be role = STAFF; a Head Admin account can never be modified or
 * deleted through this endpoint (including the caller's own account, so a
 * Head Admin can't accidentally lock themselves out or demote themselves
 * here).
 */
require_once '../../../config/database.php';
require_once '../../../config/cloudinary.php';
require_once '../../../config/crypto.php';

$method = $_SERVER['REQUEST_METHOD'];
$headAdmin = requireHeadAdmin($conn);

const STAFF_LIST_COLUMNS = "
    id, name, phone, username, email, role, approval_status, photo_url,
    age, gender, created_at, updated_at,
    (gemini_api_key IS NOT NULL AND gemini_api_key <> '') AS has_gemini_key
";

function getStaffRowOrFail($conn, $id) {
    $stmt = $conn->prepare("SELECT " . STAFF_LIST_COLUMNS . " FROM admins WHERE id = ? AND role = 'STAFF'");
    $stmt->execute([$id]);
    $row = $stmt->fetch();
    if (!$row) {
        sendJson(false, "Staff member not found", null, 404);
    }
    $row['has_gemini_key'] = (bool) $row['has_gemini_key'];
    return $row;
}

if ($method === 'GET') {
    $id = $_GET['id'] ?? null;
    if ($id !== null) {
        $staff = getStaffRowOrFail($conn, (int) $id);
        sendJson(true, "Staff detail loaded", $staff);
    }

    $stmt = $conn->query("SELECT " . STAFF_LIST_COLUMNS . " FROM admins WHERE role = 'STAFF' ORDER BY created_at DESC");
    $staff = $stmt->fetchAll();
    foreach ($staff as &$row) {
        $row['has_gemini_key'] = (bool) $row['has_gemini_key'];
    }
    unset($row);

    sendJson(true, "Staff list loaded", $staff);
}

if ($method === 'POST') {
    $data = readJsonBody();
    $id = isset($data->id) ? (int) $data->id : 0;
    $action = trim($data->action ?? '');

    if (!$id || !in_array($action, ['approve', 'reject', 'disable', 'enable'], true)) {
        sendJson(false, "A valid 'id' and 'action' (approve, reject, disable, or enable) are required", null, 422);
    }

    // Confirms the target exists and really is a STAFF row (never a Head Admin).
    getStaffRowOrFail($conn, $id);

    $newStatus = [
        'approve' => 'APPROVED',
        'reject' => 'REJECTED',
        'disable' => 'DISABLED',
        'enable' => 'APPROVED',
    ][$action];

    $stmt = $conn->prepare("UPDATE admins SET approval_status = ? WHERE id = ? AND role = 'STAFF'");
    $stmt->execute([$newStatus, $id]);

    // Disabling/rejecting should immediately kill any active session for that account.
    if (in_array($action, ['disable', 'reject'], true)) {
        $stmt = $conn->prepare("UPDATE admins SET token = NULL, token_expires_at = NULL WHERE id = ?");
        $stmt->execute([$id]);
    }

    $staff = getStaffRowOrFail($conn, $id);
    sendJson(true, "Staff account updated", $staff);
}

if ($method === 'PUT') {
    $id = isset($_GET['id']) ? (int) $_GET['id'] : 0;
    if (!$id) {
        sendJson(false, "id query parameter is required", null, 422);
    }
    // Confirms the target exists and really is a STAFF row.
    getStaffRowOrFail($conn, $id);

    $data = readJsonBody();
    $fields = [];
    $params = [];

    if (isset($data->name)) {
        $name = trim($data->name);
        if ($name === '') sendJson(false, "Name cannot be empty", null, 422);
        $fields[] = "name = ?";
        $params[] = $name;
    }
    if (isset($data->phone)) {
        $phone = trim($data->phone);
        if ($phone === '') sendJson(false, "Phone cannot be empty", null, 422);
        $fields[] = "phone = ?";
        $params[] = $phone;
    }
    if (isset($data->age)) {
        $age = $data->age === null || $data->age === '' ? null : (int) $data->age;
        if ($age !== null && ($age < 16 || $age > 100)) {
            sendJson(false, "Age must be between 16 and 100", null, 422);
        }
        $fields[] = "age = ?";
        $params[] = $age;
    }
    if (isset($data->gender)) {
        $gender = trim($data->gender);
        if ($gender !== '' && !in_array($gender, ['Male', 'Female', 'Other'], true)) {
            sendJson(false, "Gender must be Male, Female or Other", null, 422);
        }
        $fields[] = "gender = ?";
        $params[] = $gender ?: null;
    }
    if (isset($data->username)) {
        $username = trim($data->username);
        if (!preg_match('/^[a-zA-Z0-9_.]{3,50}$/', $username)) {
            sendJson(false, "Username must be 3-50 characters (letters, numbers, _ or .)", null, 422);
        }
        $stmt = $conn->prepare("SELECT id FROM admins WHERE username = ? AND id <> ?");
        $stmt->execute([$username, $id]);
        if ($stmt->fetch()) {
            sendJson(false, "That username is already taken", null, 409);
        }
        $fields[] = "username = ?";
        $params[] = $username;
    }
    if (isset($data->password) && trim((string) $data->password) !== '') {
        $password = (string) $data->password;
        if (strlen($password) < 6) sendJson(false, "Password must be at least 6 characters", null, 422);
        $fields[] = "password = ?";
        $params[] = password_hash($password, PASSWORD_BCRYPT);
    }
    // Only touch the Gemini key if the caller actually sent a non-blank value -
    // leaving it blank/omitted keeps the staff member's existing key untouched.
    if (isset($data->gemini_api_key) && trim((string) $data->gemini_api_key) !== '') {
        $fields[] = "gemini_api_key = ?";
        $params[] = encryptSecretValue(trim((string) $data->gemini_api_key), $CONFIG);
    }

    if (empty($fields)) {
        sendJson(false, "No editable fields were provided", null, 422);
    }

    $params[] = $id;
    $stmt = $conn->prepare("UPDATE admins SET " . implode(", ", $fields) . " WHERE id = ? AND role = 'STAFF'");
    $stmt->execute($params);

    $staff = getStaffRowOrFail($conn, $id);
    sendJson(true, "Staff profile updated", $staff);
}

if ($method === 'DELETE') {
    $id = isset($_GET['id']) ? (int) $_GET['id'] : 0;
    if (!$id) {
        sendJson(false, "id query parameter is required", null, 422);
    }
    $staff = getStaffRowOrFail($conn, $id);

    $stmt = $conn->prepare("DELETE FROM admins WHERE id = ? AND role = 'STAFF'");
    $stmt->execute([$id]);

    sendJson(true, "Staff account deleted", ["id" => $id]);
}

sendJson(false, "Method not allowed", null, 405);
