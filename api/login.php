<?php

declare(strict_types=1);

/**
 * POST JSON: { "login"|"username"|"email", "password", "role": "user"|"admin" }
 * User role → `users` table. Admin role → `admins` table.
 */

require_once __DIR__ . '/bootstrap.php';

start_session_secure();
header('Content-Type: application/json; charset=utf-8');

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    json_response(405, ['error' => 'Method not allowed']);
    exit;
}

try {
    $body = read_json_body();
    $password = isset($body['password']) && is_string($body['password']) ? $body['password'] : '';

    $login = '';
    if (isset($body['login']) && is_string($body['login'])) {
        $login = trim($body['login']);
    } elseif (isset($body['username']) && is_string($body['username'])) {
        $login = trim($body['username']);
    } elseif (isset($body['email']) && is_string($body['email'])) {
        $login = trim($body['email']);
    }

    $role = isset($body['role']) && $body['role'] === 'admin' ? 'admin' : 'user';

    if ($login === '' || $password === '') {
        json_response(400, ['error' => 'login (or username/email), password, and role are required']);
        exit;
    }

    if ($role === 'admin') {
        $repo = new AdminRepository($mysqli);
        $creds = $repo->findCredentialsByLogin($login);
        if ($creds === null || !password_verify($password, $creds['password'])) {
            json_response(401, ['error' => 'Invalid login or password']);
            exit;
        }
        $id = $creds['admin_id'];
        $profile = $repo->findById($id);
    } else {
        $repo = new UserRepository($mysqli);
        $creds = $repo->findCredentialsByLogin($login);
        if ($creds === null || !password_verify($password, $creds['password'])) {
            json_response(401, ['error' => 'Invalid login or password']);
            exit;
        }
        $id = $creds['user_id'];
        $profile = $repo->findById($id);
    }

    session_regenerate_id(true);
    $_SESSION['account_type'] = $role;
    $_SESSION['account_id'] = $id;
    unset($_SESSION['user_id']);

    json_response(200, [
        'success'       => true,
        'account_type'  => $role,
        'user_id'       => $id,
        'user'          => $profile,
    ]);
} catch (JsonException $e) {
    json_response(400, ['error' => 'Invalid JSON body']);
    exit;
} catch (mysqli_sql_exception $e) {
    error_log('login DB: ' . $e->getMessage());
    json_response(500, ['error' => 'Database error']);
    exit;
}
