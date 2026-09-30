CREATE DATABASE IF NOT EXISTS expense_tracker_web CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE expense_tracker_web;

CREATE TABLE IF NOT EXISTS users (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS categories (
  id      INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  name    VARCHAR(50) NOT NULL,
  color   CHAR(7) NOT NULL DEFAULT '#6366f1',
  UNIQUE KEY uq_user_category (user_id, name),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expenses (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  user_id      INT NOT NULL,
  category_id  INT NULL,
  title        VARCHAR(150) NOT NULL,
  amount       DECIMAL(12,2) NOT NULL,
  expense_date DATE NOT NULL,
  note         VARCHAR(500) NULL,
  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_date (user_id, expense_date),
  FOREIGN KEY (user_id)     REFERENCES users(id)      ON DELETE CASCADE,
  FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);
