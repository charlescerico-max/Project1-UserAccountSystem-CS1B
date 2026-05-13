<?php

declare(strict_types=1);

/**
 * POST JSON: { "current_password": "...", "new_password": "..." }
 * Updates password for the logged-in row in `users` or `admins`.
 */

require_once __DIR__ . '/bootstrap.php';

start_session_secure();
header('Content-Type: application/json; charset=utf-8');

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_response(405, ['success' => false, 'message' => 'Method not allowed']);
    exit;
}

$type = $_SESSION['account_type'] ?? null;
$id = isset($_SESSION['account_id']) ? filter_var($_SESSION['account_id'], FILTER_VALIDATE_INT) : false;

if (($type === null || $type === '') && isset($_SESSION['user_id'])) {
    $type = 'user';
    $id = filter_var($_SESSION['user_id'], FILTER_VALIDATE_INT);
}

if (!is_string($type) || ($type !== 'user' && $type !== 'admin') || $id === false || $id < 1) {
    json_response(401, ['success' => false, 'message' => 'Not authenticated. Please log in again.']);
    exit;
}

/**
 * Same rules as security.html client policy.
 */
function password_meets_policy(string $p): bool
{
    if (strlen($p) < 8) {
        return false;
    }
    if (!preg_match('/[A-Z]/', $p)) {
        return false;
    }
    if (!preg_match('/[0-9]/', $p)) {
        return false;
    }
    if (!preg_match('/[@#!%_]/', $p)) {
        return false;
    }
    return true;
}

try {
    $body = read_json_body();
    $current = isset($body['current_password']) && is_string($body['current_password'])
        ? $body['current_password']
        : (isset($body['currentPassword']) && is_string($body['currentPassword']) ? $body['currentPassword'] : '');
    $new = isset($body['new_password']) && is_string($body['new_password'])
        ? $body['new_password']
        : (isset($body['newPassword']) && is_string($body['newPassword']) ? $body['newPassword'] : '');

    if ($current === '' || $new === '') {
        json_response(400, ['success' => false, 'message' => 'Current password and new password are required.']);
        exit;
    }

    if (!password_meets_policy($new)) {
        json_response(400, [
            'success' => false,
            'message'   => 'The new password must be at least 8 characters and include one uppercase letter, one number, and one special character (@, #, !, %, or _).',
        ]);
        exit;
    }

    $table = $type === 'admin' ? 'admins' : 'users';
    $idCol = $type === 'admin' ? 'admin_user_id' : 'user_id';
    $passCol = $type === 'admin' ? 'admin_password' : 'password';

    $stmt = $mysqli->prepare("SELECT {$passCol} FROM {$table} WHERE {$idCol} = ? LIMIT 1");
    $stmt->bind_param('i', $id);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    if ($row === null || !password_verify($current, (string) ($row[$passCol] ?? ''))) {
        json_response(401, ['success' => false, 'message' => 'Current password is incorrect.']);
        exit;
    }

    $hash = password_hash($new, PASSWORD_DEFAULT);
    $upd = $mysqli->prepare("UPDATE {$table} SET {$passCol} = ? WHERE {$idCol} = ?");
    $upd->bind_param('si', $hash, $id);
    $upd->execute();
    $upd->close();

    json_response(200, ['success' => true, 'message' => 'Password changed successfully.']);
} catch (JsonException $e) {
    json_response(400, ['success' => false, 'message' => 'Invalid JSON body']);
    exit;
} catch (mysqli_sql_exception $e) {
    error_log('change-password DB: ' . $e->getMessage());
    json_response(500, ['success' => false, 'message' => 'Database error']);
    exit;
}
