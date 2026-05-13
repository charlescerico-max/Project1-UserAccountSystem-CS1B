<?php

declare(strict_types=1);

/**
 * Ends the session (any method). Returns JSON { "ok": true }.
 */

require_once __DIR__ . '/bootstrap.php';

start_session_secure();
header('Content-Type: application/json; charset=utf-8');

$_SESSION = [];
if (ini_get('session.use_cookies')) {
    $p = session_get_cookie_params();
    setcookie(session_name(), '', time() - 42000, $p['path'], $p['domain'], (bool) $p['secure'], (bool) $p['httponly']);
}
session_destroy();

json_response(200, ['ok' => true]);
