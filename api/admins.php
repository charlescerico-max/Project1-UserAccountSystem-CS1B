<?php

declare(strict_types=1);

/**
 * Admins CRUD API (JSON) — `admins` table.
 *
 * - GET    admins.php           → list admins
 * - GET    admins.php?id=1      → one admin
 * - POST   admins.php           → create (JSON body)
 * - PUT    admins.php           → update (JSON body, include admin_id)
 * - DELETE admins.php?id=1     → delete
 */

require_once __DIR__ . '/bootstrap.php';

header('Content-Type: application/json; charset=utf-8');

$repo = new AdminRepository($mysqli);

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

try {
    switch ($method) {
        case 'GET':
            if (isset($_GET['id']) && $_GET['id'] !== '') {
                $id = (int) $_GET['id'];
                if ($id < 1) {
                    json_response(400, ['error' => 'Invalid admin id']);
                    exit;
                }
                $admin = $repo->findById($id);
                if ($admin === null) {
                    json_response(404, ['error' => 'Admin not found']);
                    exit;
                }
                json_response(200, ['admin' => $admin]);
                exit;
            }
            json_response(200, ['admins' => $repo->findAll()]);
            exit;

        case 'POST':
            $body = read_json_body();
            $required = ['first_name', 'last_name', 'email', 'username', 'password'];
            foreach ($required as $key) {
                if (!isset($body[$key]) || !is_string($body[$key]) || trim($body[$key]) === '') {
                    json_response(400, ['error' => "Missing or empty field: {$key}"]);
                    exit;
                }
            }
            $createdBy = null;
            if (isset($body['created_by'])) {
                $createdBy = $body['created_by'] === null ? null : (int) $body['created_by'];
            }
            $updatedBy = null;
            if (isset($body['updated_by'])) {
                $updatedBy = $body['updated_by'] === null ? null : (int) $body['updated_by'];
            }
            $newId = $repo->create([
                'first_name' => trim($body['first_name']),
                'last_name'  => trim($body['last_name']),
                'email'      => trim($body['email']),
                'username'   => trim($body['username']),
                'password'   => $body['password'],
                'created_by' => $createdBy,
                'updated_by' => $updatedBy,
            ]);
            $admin = $repo->findById($newId);
            json_response(201, ['admin' => $admin]);
            exit;

        case 'PUT':
        case 'PATCH':
            $body = read_json_body();
            if (!isset($body['admin_id'])) {
                json_response(400, ['error' => 'admin_id is required for update']);
                exit;
            }
            $id = (int) $body['admin_id'];
            if ($id < 1) {
                json_response(400, ['error' => 'Invalid admin_id']);
                exit;
            }
            $patch = [];
            foreach (['first_name', 'last_name', 'email', 'username', 'password'] as $f) {
                if (!array_key_exists($f, $body)) {
                    continue;
                }
                if ($f === 'password' && ($body[$f] === null || $body[$f] === '')) {
                    continue;
                }
                if (!is_string($body[$f])) {
                    json_response(400, ['error' => "Field {$f} must be a string"]);
                    exit;
                }
                $patch[$f] = $f === 'password' ? $body[$f] : trim($body[$f]);
            }
            if (array_key_exists('updated_by', $body)) {
                $patch['updated_by'] = $body['updated_by'] === null ? null : (int) $body['updated_by'];
            }
            if ($patch === []) {
                json_response(400, ['error' => 'No fields to update']);
                exit;
            }
            if (!$repo->update($id, $patch)) {
                json_response(404, ['error' => 'Admin not found or nothing changed']);
                exit;
            }
            json_response(200, ['admin' => $repo->findById($id)]);
            exit;

        case 'DELETE':
            if (!isset($_GET['id']) || $_GET['id'] === '') {
                json_response(400, ['error' => 'Query parameter id is required']);
                exit;
            }
            $delId = (int) $_GET['id'];
            if ($delId < 1) {
                json_response(400, ['error' => 'Invalid admin id']);
                exit;
            }
            if (!$repo->delete($delId)) {
                json_response(404, ['error' => 'Admin not found']);
                exit;
            }
            json_response(200, ['ok' => true, 'deleted_admin_id' => $delId]);
            exit;

        default:
            json_response(405, ['error' => 'Method not allowed']);
            exit;
    }
} catch (mysqli_sql_exception $e) {
    error_log('admins API DB: ' . $e->getMessage());
    if ((int) $e->getCode() === 1062) {
        json_response(409, ['error' => 'Email or username already exists']);
        exit;
    }
    json_response(500, ['error' => 'Database error']);
    exit;
} catch (JsonException $e) {
    json_response(400, ['error' => 'Invalid JSON body']);
    exit;
}
