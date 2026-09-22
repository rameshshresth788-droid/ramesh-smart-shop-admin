-- =========================================================
-- RAMESH SMART SHOP - Staff Management Migration v2
-- Safe to run multiple times, and safe to run regardless of which
-- earlier migrations (if any) already ran on this database - every
-- change below checks INFORMATION_SCHEMA first and is a no-op if the
-- column/index/data already exists in the expected shape.
--
-- Run this on the LIVE database before deploying the updated backend.
-- Does NOT drop or destroy any existing table or data.
-- =========================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS rss_add_column_if_missing $$
CREATE PROCEDURE rss_add_column_if_missing(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_coldef TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @rss_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_coldef);
        PREPARE rss_stmt FROM @rss_ddl;
        EXECUTE rss_stmt;
        DEALLOCATE PREPARE rss_stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS rss_add_index_if_missing $$
CREATE PROCEDURE rss_add_index_if_missing(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_coldef VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @rss_ddl = CONCAT('CREATE INDEX `', p_index, '` ON `', p_table, '` (', p_coldef, ')');
        PREPARE rss_stmt FROM @rss_ddl;
        EXECUTE rss_stmt;
        DEALLOCATE PREPARE rss_stmt;
    END IF;
END $$

DELIMITER ;

-- ---- Columns (matches what backend/config/database.php and the staff
--      endpoints expect to exist on `admins`) ----
CALL rss_add_column_if_missing('admins', 'role', "ENUM('HEAD_ADMIN','STAFF') NOT NULL DEFAULT 'HEAD_ADMIN'");
CALL rss_add_column_if_missing('admins', 'approval_status', "ENUM('PENDING','APPROVED','REJECTED','DISABLED') NOT NULL DEFAULT 'APPROVED'");
CALL rss_add_column_if_missing('admins', 'photo_url', "VARCHAR(500) DEFAULT NULL");
CALL rss_add_column_if_missing('admins', 'age', "INT DEFAULT NULL");
CALL rss_add_column_if_missing('admins', 'gender', "VARCHAR(30) DEFAULT NULL");
CALL rss_add_column_if_missing('admins', 'gemini_api_key', "TEXT DEFAULT NULL");
CALL rss_add_column_if_missing('admins', 'updated_at', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
CALL rss_add_column_if_missing('admins', 'name', "VARCHAR(100) DEFAULT NULL");
CALL rss_add_column_if_missing('admins', 'phone', "VARCHAR(30) DEFAULT NULL");

-- ---- Indexes ----
CALL rss_add_index_if_missing('admins', 'idx_admins_role', 'role');
CALL rss_add_index_if_missing('admins', 'idx_admins_approval_status', 'approval_status');

-- ---- Relax `email` to nullable ----
-- Staff registration does not collect an email address, so `email` must
-- allow NULL (MySQL's UNIQUE index allows any number of NULLs, so this
-- does not weaken the existing uniqueness guarantee for admins that do
-- have an email). No-op if email is already nullable.
SET @rss_email_nullable = (
    SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admins' AND COLUMN_NAME = 'email'
);
SET @rss_ddl = IF(@rss_email_nullable = 'NO', 'ALTER TABLE `admins` MODIFY `email` VARCHAR(100) NULL', 'SELECT 1');
PREPARE rss_stmt FROM @rss_ddl;
EXECUTE rss_stmt;
DEALLOCATE PREPARE rss_stmt;

-- ---- Backfill ----
-- Any pre-existing admin row (e.g. the original default Head Admin) that
-- has no `name` yet gets its username copied in, purely so the UI never
-- shows a blank name. Harmless if it has already run before.
UPDATE admins SET name = username WHERE name IS NULL OR name = '';

-- ---- Cleanup ----
DROP PROCEDURE IF EXISTS rss_add_column_if_missing;
DROP PROCEDURE IF EXISTS rss_add_index_if_missing;
