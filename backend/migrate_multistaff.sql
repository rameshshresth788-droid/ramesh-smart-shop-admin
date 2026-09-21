-- RAMESH SMART SHOP - Multi Staff Admin Migration
-- Run this ONLY on the existing live database.
-- Do NOT import database.sql again.

ALTER TABLE admins
    ADD COLUMN role ENUM('HEAD_ADMIN','STAFF') NOT NULL DEFAULT 'HEAD_ADMIN',
    ADD COLUMN approval_status ENUM('PENDING','APPROVED','REJECTED','DISABLED') NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN photo_url VARCHAR(500) DEFAULT NULL,
    ADD COLUMN age INT DEFAULT NULL,
    ADD COLUMN gender VARCHAR(30) DEFAULT NULL,
    ADD COLUMN gemini_api_key TEXT DEFAULT NULL,
    ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE INDEX idx_admins_role ON admins(role);
CREATE INDEX idx_admins_approval_status ON admins(approval_status);
