-- =========================================================
-- RAMESH SMART SHOP - Database Schema
-- Import this file fresh in phpMyAdmin (it will create the DB).
-- =========================================================

CREATE DATABASE IF NOT EXISTS ramesh_smart_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ramesh_smart_shop;

-- ---------------------------------------------------------
-- Admins
-- ---------------------------------------------------------
CREATE TABLE admins (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,          -- bcrypt hash, never plain text
    token VARCHAR(100) DEFAULT NULL,
    token_expires_at DATETIME DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- DEVELOPMENT ONLY default admin. Username: admin  Password: admin123
-- CHANGE THIS PASSWORD BEFORE GOING LIVE.
INSERT INTO admins (username, email, password) VALUES
('admin', 'admin@example.com', '$2b$12$Nqco7IqhZ17sUPeEItCgM.RHW1GxtfygEADwYppJT4HilFYipG0.u');

-- ---------------------------------------------------------
-- Products
-- ---------------------------------------------------------
CREATE TABLE products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(150) NOT NULL,
    category VARCHAR(80) NOT NULL,
    product_type VARCHAR(80) DEFAULT NULL,
    description TEXT,
    current_price DECIMAL(10,2) NOT NULL,
    old_price DECIMAL(10,2) DEFAULT NULL,
    image_url VARCHAR(500) DEFAULT NULL,
    cloudinary_public_id VARCHAR(255) DEFAULT NULL,
    status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_category (category),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- Users (temporary shopping sessions - no accounts/passwords)
-- ---------------------------------------------------------
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    gender VARCHAR(30) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- Purchase requests (customer -> admin, pending approval/pickup)
-- ---------------------------------------------------------
CREATE TABLE purchase_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) DEFAULT NULL,
    customer_name VARCHAR(100) NOT NULL,
    gender VARCHAR(30) DEFAULT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status ENUM('PENDING','COMPLETED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(80) DEFAULT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_session_id (session_id)
) ENGINE=InnoDB;

CREATE TABLE purchase_request_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    request_id INT NOT NULL,
    product_id INT DEFAULT NULL,
    product_name VARCHAR(150) NOT NULL,
    image_url VARCHAR(500) DEFAULT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,   -- price snapshot at request time
    subtotal DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (request_id) REFERENCES purchase_requests(id) ON DELETE CASCADE,
    INDEX idx_request_id (request_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- Purchases (finalized/completed sales - shown in 24h history)
-- ---------------------------------------------------------
CREATE TABLE purchases (
    id INT AUTO_INCREMENT PRIMARY KEY,
    request_id INT DEFAULT NULL,
    customer_name VARCHAR(100) NOT NULL,
    gender VARCHAR(30) DEFAULT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_completed_at (completed_at)
) ENGINE=InnoDB;

CREATE TABLE purchase_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    purchase_id INT NOT NULL,
    product_id INT DEFAULT NULL,
    product_name VARCHAR(150) NOT NULL,
    image_url VARCHAR(500) DEFAULT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE,
    INDEX idx_purchase_id (purchase_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- Settings (key/value store; secrets stay server-side only)
-- ---------------------------------------------------------
CREATE TABLE settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT
) ENGINE=InnoDB;

INSERT INTO settings (setting_key, setting_value) VALUES
('shop_name', 'Ramesh Smart Shop'),
('currency_symbol', '₹'),
('purchase_history_hours', '24'),
('request_expiry_minutes', '120'),
('ai_provider', 'gemini'),
('ai_model', 'gemini-2.5-flash'),
('ai_endpoint', ''),
('ai_api_key', ''),
('cloudinary_cloud_name', ''),
('cloudinary_api_key', ''),
('cloudinary_api_secret', ''),
('website_origin', '*');
