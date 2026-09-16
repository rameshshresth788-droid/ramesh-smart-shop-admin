import os

backend_api_settings_index_php = """
<?php
require_once '../../config/database.php';
verifyAdmin($conn);

$method = $_SERVER['REQUEST_METHOD'];

if ($method == 'GET') {
    $stmt = $conn->query("SELECT setting_key, setting_value FROM settings");
    $settings = [];
    while ($row = $stmt->fetch()) {
        $settings[$row['setting_key']] = $row['setting_value'];
    }
    sendJson(true, "Settings loaded", $settings);
} elseif ($method == 'POST') {
    $data = json_decode(file_get_contents("php://input"), true);
    if (is_array($data)) {
        foreach ($data as $key => $value) {
            $stmt = $conn->prepare("INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE setting_value = ?");
            $stmt->execute([$key, $value, $value]);
        }
    }
    sendJson(true, "Settings updated");
}
?>
"""

settings_sql = """
CREATE TABLE settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT
);
INSERT INTO settings (setting_key, setting_value) VALUES ('shop_name', 'Ramesh Smart Shop');
INSERT INTO settings (setting_key, setting_value) VALUES ('currency', '₹');
"""

with open('backend/api/settings/index.php', 'w') as f:
    f.write(backend_api_settings_index_php.strip() + '\n')

with open('backend/database.sql', 'a') as f:
    f.write('\n' + settings_sql.strip() + '\n')

print("Settings generated")
