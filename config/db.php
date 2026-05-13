<?php
/**
 * MySQL connection for accsystem_db (XAMPP / local) via mysqli.
 * Usage: require_once __DIR__ . '/../config/db.php';  then use $mysqli
 */

declare(strict_types=1);

$DB_HOST = '127.0.0.1';
$DB_PORT = 3306;
$DB_NAME = 'accsystem_db';
$DB_USER = 'root';
$DB_PASS = '';
$DB_CHARSET = 'utf8mb4';

mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);

try {
    $mysqli = new mysqli($DB_HOST, $DB_USER, $DB_PASS, $DB_NAME, $DB_PORT);
} catch (mysqli_sql_exception $e) {
    error_log('Database connection failed: ' . $e->getMessage());
    if (!headers_sent()) {
        http_response_code(500);
    }
    exit('Could not connect to the database.');
}

$mysqli->set_charset($DB_CHARSET);
