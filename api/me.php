<?php

declare(strict_types=1);

/**
 * GET — current account from session (`users` or `admins` by account_type).
 */

require_once __DIR__ . '/bootstrap.php';

start_session_secure();
header('Content-Type: application/json; charset=utf-8');

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_response(405, ['error' => 'Method not allowed']);
    exit;
}

$type = $_SESSION['account_type'] ?? null;
$id = isset($_SESSION['account_id']) ? filter_var($_SESSION['account_id'], FILTER_VALIDATE_INT) : false;

if (($type === null || $type === '') && isset($_SESSION['user_id'])) {
    $type = 'user';
    $id = filter_var($_SESSION['user_id'], FILTER_VALIDATE_INT);
}

if (!is_string($type) || ($type !== 'user' && $type !== 'admin') || $id === false || $id < 1) {
    json_response(401, ['error' => 'Not authenticated']);
    exit;
}

try {
    if ($type === 'admin') {
        $repo = new AdminRepository($mysqli);
        $row = $repo->findById($id);
    } else {
        $repo = new UserRepository($mysqli);
        $row = $repo->findById($id);
    }

    if ($row === null) {
        $_SESSION = [];
        session_destroy();
        json_response(401, ['error' => 'Not authenticated']);
        exit;
    }

    json_response(200, [
        'account_type' => $type,
        'user'         => $row,
    ]);
} catch (mysqli_sql_exception $e) {
    error_log('me DB: ' . $e->getMessage());
    json_response(500, ['error' => 'Database error']);
    exit;
}
