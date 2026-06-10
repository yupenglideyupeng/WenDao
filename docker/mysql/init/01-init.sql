-- ============================================================
-- WenDao Database Initialization
-- ============================================================
-- This SQL runs automatically when the MySQL container starts
-- for the first time (only if the data volume is empty).
--
-- The database 'wendao' is created by MYSQL_DATABASE env var.
-- This script runs within that database context.
-- ============================================================

-- Grant privileges for the wendao user (in case of custom DB_USERNAME)
-- The default root user from MYSQL_ROOT_PASSWORD already has full access.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
