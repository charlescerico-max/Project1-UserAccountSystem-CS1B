<?php

declare(strict_types=1);

/**
 * CRUD for `users` (accsystem_db) using mysqli.
 */
final class UserRepository
{
    public function __construct(private mysqli $mysqli)
    {
    }

    /**
     * @param list<mixed> $values
     */
    private static function stmtBind(mysqli_stmt $stmt, string $types, array $values): void
    {
        $params = [$types];
        foreach ($values as &$v) {
            $params[] = &$v;
        }
        unset($v);
        call_user_func_array([$stmt, 'bind_param'], $params);
    }

    /** @return list<array<string, mixed>> */
    public function findAll(): array
    {
        $sql = 'SELECT user_id, first_name, last_name, email, username, created_at, created_by, updated_by, updated_at
                FROM users ORDER BY user_id ASC';
        $result = $this->mysqli->query($sql);
        $rows = [];
        while ($row = $result->fetch_assoc()) {
            $rows[] = $row;
        }
        $result->free();
        return $rows;
    }

    /** @return array<string, mixed>|null */
    public function findById(int $userId): ?array
    {
        $stmt = $this->mysqli->prepare(
            'SELECT user_id, first_name, last_name, email, username, created_at, created_by, updated_by, updated_at
             FROM users WHERE user_id = ? LIMIT 1'
        );
        self::stmtBind($stmt, 'i', [$userId]);
        $stmt->execute();
        $res = $stmt->get_result();
        if ($res === false) {
            $stmt->close();
            return null;
        }
        $row = $res->fetch_assoc();
        $stmt->close();
        return $row === null ? null : $row;
    }

    /**
     * @return array{user_id: int, password: string}|null
     */
    public function findCredentialsByLogin(string $usernameOrEmail): ?array
    {
        $stmt = $this->mysqli->prepare(
            'SELECT user_id, password FROM users
             WHERE username = ? OR email = ?
             LIMIT 1'
        );
        self::stmtBind($stmt, 'ss', [$usernameOrEmail, $usernameOrEmail]);
        $stmt->execute();
        $res = $stmt->get_result();
        if ($res === false) {
            $stmt->close();
            return null;
        }
        $row = $res->fetch_assoc();
        $stmt->close();
        if ($row === null) {
            return null;
        }
        return [
            'user_id'  => (int) $row['user_id'],
            'password' => (string) $row['password'],
        ];
    }

    /**
     * @param array{
     *   first_name: string,
     *   last_name: string,
     *   email: string,
     *   username: string,
     *   password: string,
     *   created_by?: int|null,
     *   updated_by?: int|null
     * } $data
     */
    public function create(array $data): int
    {
        $hash = password_hash($data['password'], PASSWORD_DEFAULT);
        $cb = $data['created_by'] ?? null;
        $ub = $data['updated_by'] ?? null;

        $cols = ['first_name', 'last_name', 'email', 'username', 'password'];
        $placeholders = ['?', '?', '?', '?', '?'];
        $types = 'sssss';
        $values = [
            $data['first_name'],
            $data['last_name'],
            $data['email'],
            $data['username'],
            $hash,
        ];

        if ($cb !== null) {
            $cols[] = 'created_by';
            $placeholders[] = '?';
            $types .= 'i';
            $values[] = $cb;
        }
        if ($ub !== null) {
            $cols[] = 'updated_by';
            $placeholders[] = '?';
            $types .= 'i';
            $values[] = $ub;
        }

        $sql = 'INSERT INTO users (' . implode(', ', $cols) . ') VALUES (' . implode(', ', $placeholders) . ')';
        $stmt = $this->mysqli->prepare($sql);
        self::stmtBind($stmt, $types, $values);
        $stmt->execute();
        $id = (int) $this->mysqli->insert_id;
        $stmt->close();
        return $id;
    }

    /**
     * @param array{
     *   first_name?: string,
     *   last_name?: string,
     *   email?: string,
     *   username?: string,
     *   password?: string,
     *   updated_by?: int|null
     * } $data
     */
    public function update(int $userId, array $data): bool
    {
        $sets = [];
        $types = '';
        $values = [];

        if (isset($data['first_name'])) {
            $sets[] = 'first_name = ?';
            $types .= 's';
            $values[] = $data['first_name'];
        }
        if (isset($data['last_name'])) {
            $sets[] = 'last_name = ?';
            $types .= 's';
            $values[] = $data['last_name'];
        }
        if (isset($data['email'])) {
            $sets[] = 'email = ?';
            $types .= 's';
            $values[] = $data['email'];
        }
        if (isset($data['username'])) {
            $sets[] = 'username = ?';
            $types .= 's';
            $values[] = $data['username'];
        }
        if (isset($data['password']) && $data['password'] !== '') {
            $sets[] = 'password = ?';
            $types .= 's';
            $values[] = password_hash($data['password'], PASSWORD_DEFAULT);
        }
        if (array_key_exists('updated_by', $data)) {
            $sets[] = 'updated_by = ?';
            $types .= 'i';
            $values[] = $data['updated_by'];
        }

        if ($sets === []) {
            return false;
        }

        $types .= 'i';
        $values[] = $userId;

        $sql = 'UPDATE users SET ' . implode(', ', $sets) . ' WHERE user_id = ?';
        $stmt = $this->mysqli->prepare($sql);
        self::stmtBind($stmt, $types, $values);
        $stmt->execute();
        $n = $stmt->affected_rows;
        $stmt->close();
        return $n > 0;
    }

    public function delete(int $userId): bool
    {
        $stmt = $this->mysqli->prepare('DELETE FROM users WHERE user_id = ?');
        self::stmtBind($stmt, 'i', [$userId]);
        $stmt->execute();
        $n = $stmt->affected_rows;
        $stmt->close();
        return $n > 0;
    }
}
