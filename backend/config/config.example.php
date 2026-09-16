<?php
/**
 * COPY THIS FILE to config.php and fill in your real values.
 * config.php is in .gitignore - never commit real credentials.
 *
 * Only things that MUST be known before the database can be reached
 * live here (DB credentials, CORS origin). Everything else
 * (AI key, Cloudinary secret, shop name, etc.) is stored in the
 * `settings` DB table and is editable from the Admin App -> Settings
 * screen without ever touching this file or rebuilding the APK.
 */

return [
    // ---- MySQL connection ----
    'db_host'     => 'localhost',
    'db_name'     => 'ramesh_smart_shop',
    'db_user'     => 'root',
    'db_password' => '',

    // ---- CORS ----
    // Set this to your exact user-website origin in production, e.g.
    // 'https://myshop.com'  (no trailing slash).
    // Using '*' is fine for local testing only, since the website does
    // not send credentials/cookies - but set a real origin for production.
    'allowed_origin' => '*',

    // ---- Admin session ----
    // How long an admin login token stays valid, in hours.
    'admin_token_ttl_hours' => 72,
];
