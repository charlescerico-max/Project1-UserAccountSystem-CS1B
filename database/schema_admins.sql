-- Admins table (matches typical accsystem_db layout with admin_* columns).
-- If you already have this table, skip.
CREATE TABLE IF NOT EXISTS `admins` (
  `admin_user_id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_first_name` VARCHAR(100) NOT NULL,
  `admin_last_name` VARCHAR(100) NOT NULL,
  `admin_email` VARCHAR(255) NOT NULL,
  `admin_username` VARCHAR(100) NOT NULL,
  `admin_password` VARCHAR(255) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` INT UNSIGNED NULL DEFAULT NULL,
  `updated_by` INT UNSIGNED NULL DEFAULT NULL,
  `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`admin_user_id`),
  UNIQUE KEY `uq_admins_email` (`admin_email`),
  UNIQUE KEY `uq_admins_username` (`admin_username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
