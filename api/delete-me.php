<?php

declare(strict_types=1);

/**
 * POST JSON: { "password": "..." }
 * Deletes the logged-in row from `users` or `admins` after password_verify. Ends session.
 */

require_once __DIR__ . '/bootstrap.php';

start_session_secure();
header('Content-Type: application/json; charset=utf-8');

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
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
    $body = read_json_body();
    $password = isset($body['password']) && is_string($body['password']) ? $body['password'] : '';
    if ($password === '') {
        json_response(400, ['error' => 'password is required']);
        exit;
    }

    if ($type === 'admin') {
        $stmt = $mysqli->prepare('SELECT admin_password FROM admins WHERE admin_user_id = ? LIMIT 1');
        $stmt->bind_param('i', $id);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();
        if ($row === null || !password_verify($password, (string) ($row['admin_password'] ?? ''))) {
            json_response(401, ['error' => 'Invalid password']);
            exit;
        }
        $del = $mysqli->prepare('DELETE FROM admins WHERE admin_user_id = ?');
        $del->bind_param('i', $id);
        $del->execute();
        $del->close();
    } else {
        $stmt = $mysqli->prepare('SELECT password FROM users WHERE user_id = ? LIMIT 1');
        $stmt->bind_param('i', $id);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();
        if ($row === null || !password_verify($password, (string) $row['password'])) {
            json_response(401, ['error' => 'Invalid password']);
            exit;
        }
        $del = $mysqli->prepare('DELETE FROM users WHERE user_id = ?');
        $del->bind_param('i', $id);
        $del->execute();
        $del->close();
    }

    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        $p = session_get_cookie_params();
        setcookie(session_name(), '', time() - 42000, $p['path'], $p['domain'], (bool) $p['secure'], (bool) $p['httponly']);
    }
    session_destroy();

    json_response(200, ['success' => true, 'message' => 'Account deleted successfully.']);
} catch (JsonException $e) {
    json_response(400, ['error' => 'Invalid JSON body']);
    exit;
} catch (mysqli_sql_exception $e) {
    error_log('delete-me DB: ' . $e->getMessage());
    json_response(500, ['error' => 'Database error']);
    exit;
}
