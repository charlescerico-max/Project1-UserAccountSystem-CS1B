<?php

declare(strict_types=1);

require_once dirname(__DIR__) . '/config/db.php';
require_once dirname(__DIR__) . '/classes/UserRepository.php';
require_once dirname(__DIR__) . '/classes/AdminRepository.php';

/**
 * Start a PHP session with safer cookie defaults (call before any output).
 */
function start_session_secure(): void
{
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }
    session_set_cookie_params([
        'lifetime' => 0,
        'path'     => '/',
        'secure'   => isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off',
        'httponly' => true,
        'samesite' => 'Lax',
    ]);
    session_start();
}

/**
 * @param array<string, mixed>|list<mixed> $data
 */
function json_response(int $status, array $data): void
{
    http_response_code($status);
    echo json_encode($data, JSON_THROW_ON_ERROR);
}

/** @return array<string, mixed> */
function read_json_body(): array
{
    $raw = file_get_contents('php://input') ?: '';
    if ($raw === '') {
        return [];
    }
    $decoded = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
    return is_array($decoded) ? $decoded : [];
}
