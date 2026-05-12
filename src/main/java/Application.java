import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Application {

    /** Local MySQL (XAMPP): disable SSL; allowPublicKeyRetrieval avoids auth failures with mysql-connector-j + MySQL 8. */
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/accsystem_db?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    /** Regular accounts; `user` is a MySQL reserved word — must be backtick-quoted in SQL. */
    private static final String TABLE_USER = "`user`";
    private static final String TABLE_ADMIN = "admin";
    private static final String COL_USER_ID = "user_id";

    private enum AccountKind {
        USER(TABLE_USER),
        ADMIN(TABLE_ADMIN);

        final String fromClause;

        AccountKind(String fromClause) {
            this.fromClause = fromClause;
        }
    }

    static {
        // exec-maven-plugin uses an isolated classloader; DriverManager's SPI
        // auto-discovery sometimes misses it. Force-load the driver explicitly.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException ignored) {
                System.err.println("MySQL JDBC driver not on classpath. Run with `mvn exec:java` (uses pom dependency).");
            }
        }
    }

    private static String jdbcHint(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return "Password update unavailable. Check database connection.";
        }
        if (msg.contains("Communications link failure") || msg.contains("Connection refused")) {
            return "Cannot reach MySQL. Start MySQL in XAMPP (green \"Running\") and try again.";
        }
        if (msg.contains("Access denied for user")) {
            return "Database login failed. Set DB_USER / DB_PASSWORD in Application.java to match XAMPP MySQL (often root with empty password).";
        }
        if (msg.contains("Unknown database")) {
            return "Database accsystem_db not found. Create it or fix DB_URL in Application.java.";
        }
        return "Password update unavailable. Check database connection.";
    }

    private static String md5Hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    private static boolean isMd5Hex(String value) {
        return value != null && value.matches("^[a-fA-F0-9]{32}$");
    }

    private static String toMd5IfNeeded(String value) {
        if (isMd5Hex(value)) {
            return value.toLowerCase();
        }
        return md5Hash(value);
    }

    /** Login: {@code /admin/login} checks {@code admin}; other login paths check {@code user}. */
    private static Integer authenticateLogin(AccountKind kind, String username, String passwordHash) throws SQLException {
        String sql = "SELECT " + COL_USER_ID + " FROM " + kind.fromClause
                + " WHERE (username = ? OR email = ?) AND password = ? LIMIT 1";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            stmt.setString(3, passwordHash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(COL_USER_ID);
                }
            }
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/hello", new HelloHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/user/login", new LoginHandler());
        server.createContext("/admin/login", new LoginHandler());
        // "/user/profile" prefix-matches "/user/profile/update"; POST was hitting GET-only branch. Dedicated path has no prefix clash.
        server.createContext("/user/updateProfile", new UserProfileUpdateHandler(AccountKind.USER));
        server.createContext("/admin/updateProfile", new UserProfileUpdateHandler(AccountKind.ADMIN));
        server.createContext("/user/profile", new UserProfileHandler(AccountKind.USER));
        server.createContext("/admin/profile", new UserProfileHandler(AccountKind.ADMIN));
        server.createContext("/user/changePassword", new ChangePasswordHandler(AccountKind.USER));
        server.createContext("/admin/changePassword", new ChangePasswordHandler(AccountKind.ADMIN));
        server.createContext("/user/deleteAccount", new DeleteAccountHandler(AccountKind.USER));
        server.createContext("/admin/deleteAccount", new DeleteAccountHandler(AccountKind.ADMIN));
        server.createContext("/signup", new SignupHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Server started on port 8080");
    }

    static class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            String response = "Hello World!";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                String response = "{\"success\":false, \"message\":\"Method not allowed\"}";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            // Read the request body
            InputStream is = exchange.getRequestBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            String bodyStr = body.toString();
            System.out.println("Received body: " + bodyStr);
            
            // Parse form data
            Map<String, String> params = parseFormData(bodyStr);
            String username = params.get("username");
            String password = params.get("password");
            if (username != null) {
                username = username.trim();
            }
            if (password != null) {
                password = password.trim();
            }

            System.out.println("Parsed username: " + username + ", password: " + password);

            if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
                String response = "{\"success\":false, \"message\":\"Username and password required\"}";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            String response;
            try {
                String path = exchange.getRequestURI().getPath();
                AccountKind kind = "/admin/login".equals(path) ? AccountKind.ADMIN : AccountKind.USER;
                String hashedPassword = toMd5IfNeeded(password);
                Integer userId = authenticateLogin(kind, username, hashedPassword);
                if (userId != null) {
                    response = "{\"success\":true, \"user_id\":" + userId + "}";
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                } else {
                    response = "{\"success\":false, \"message\":\"Invalid credentials\"}";
                    exchange.sendResponseHeaders(401, response.getBytes().length);
                }
            } catch (SQLException e) {
                System.out.println("Authentication error: " + e.getMessage());
                e.printStackTrace();
                response = "{\"success\":false, \"message\":\"Authentication service unavailable. Check database connection.\"}";
                exchange.sendResponseHeaders(500, response.getBytes().length);
            }

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private Map<String, String> parseFormData(String data) {
            Map<String, String> params = new HashMap<>();
            if (data == null || data.isEmpty()) return params;
            String[] pairs = data.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = URLDecoder.decode(keyValue[1], "UTF-8");
                        params.put(key, value);
                        System.out.println("Parsed: " + key + " = " + value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return params;
        }
    }

    static class SignupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                String response = "{\"success\":false, \"message\":\"Method not allowed\"}";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            InputStream is = exchange.getRequestBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            Map<String, String> params = parseFormData(body.toString());
            String firstName = trimValue(params.get("firstName"));
            String lastName = trimValue(params.get("lastName"));
            String username = trimValue(params.get("username"));
            String email = trimValue(params.get("email"));
            String password = trimValue(params.get("password"));
            String confirmPassword = trimValue(params.get("confirmPassword"));

            if (isBlank(firstName) || isBlank(lastName) || isBlank(username) || isBlank(email) || isBlank(password)) {
                sendJsonResponse(exchange, 400, "{\"success\":false, \"message\":\"All fields are required\"}");
                return;
            }

            if (!password.equals(confirmPassword)) {
                sendJsonResponse(exchange, 400, "{\"success\":false, \"message\":\"Passwords do not match\"}");
                return;
            }

            if (password.length() < 6) {
                sendJsonResponse(exchange, 400, "{\"success\":false, \"message\":\"Password must be at least 6 characters\"}");
                return;
            }

            try {
                if (isUsernameOrEmailTaken(username, email)) {
                    sendJsonResponse(exchange, 409, "{\"success\":false, \"message\":\"Username or email already exists\"}");
                    return;
                }

                String hashedPassword = toMd5IfNeeded(password);
                int userId = createUser(firstName, lastName, username, email, hashedPassword);
                sendJsonResponse(exchange, 201, "{\"success\":true, \"message\":\"Account created successfully\", \"user_id\":" + userId + "}");
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"success\":false, \"message\":\"Signup service unavailable. Check database connection.\"}");
            }
        }

        private String trimValue(String value) {
            return value == null ? null : value.trim();
        }

        private boolean isBlank(String value) {
            return value == null || value.isEmpty();
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private Map<String, String> parseFormData(String data) {
            Map<String, String> params = new HashMap<>();
            if (data == null || data.isEmpty()) return params;
            String[] pairs = data.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = URLDecoder.decode(keyValue[1], "UTF-8");
                        params.put(key, value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return params;
        }

        private boolean isUsernameOrEmailTaken(String username, String email) throws SQLException {
            if (rowExists(AccountKind.USER, username, email)) {
                return true;
            }
            return rowExists(AccountKind.ADMIN, username, email);
        }

        private boolean rowExists(AccountKind kind, String username, String email) throws SQLException {
            String sql = "SELECT 1 FROM " + kind.fromClause + " WHERE username = ? OR email = ? LIMIT 1";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        }

        private int createUser(String firstName, String lastName, String username, String email, String password) throws SQLException {
            String passwordToStore = toMd5IfNeeded(password);
            String sql = "INSERT INTO " + TABLE_USER + " (first_name, last_name, username, email, password, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, firstName);
                stmt.setString(2, lastName);
                stmt.setString(3, username);
                stmt.setString(4, email);
                stmt.setString(5, passwordToStore);
                stmt.setInt(6, 1);
                stmt.setInt(7, 1);
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
            return 0;
        }
    }

    static class UserProfileHandler implements HttpHandler {

        private final AccountKind accountKind;

        UserProfileHandler(AccountKind accountKind) {
            this.accountKind = accountKind;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"success\":false, \"message\":\"Method not allowed. Use GET /user/profile or POST /user/updateProfile\"}");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            Map<String, String> queryParams = parseQueryParams(query);
            String userIdRaw = queryParams.get("user_id");
            if (userIdRaw == null || userIdRaw.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false, \"message\":\"user_id is required\"}");
                return;
            }

            int userId;
            try {
                userId = Integer.parseInt(userIdRaw.trim());
            } catch (NumberFormatException e) {
                sendJsonResponse(exchange, 400, "{\"success\":false, \"message\":\"user_id must be a number\"}");
                return;
            }

            try {
                String profileJson = getUserProfileJson(accountKind, userId);
                if (profileJson == null) {
                    sendJsonResponse(exchange, 404, "{\"success\":false, \"message\":\"User not found\"}");
                    return;
                }
                sendJsonResponse(exchange, 200, profileJson);
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"success\":false, \"message\":\"Profile service unavailable. Check database connection.\"}");
            }
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query == null || query.isEmpty()) return params;
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = URLDecoder.decode(keyValue[1], "UTF-8");
                        params.put(key, value);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return params;
        }

        private String jsonEscape(String value) {
            if (value == null) return "";
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, body.length);
            OutputStream os = exchange.getResponseBody();
            os.write(body);
            os.close();
        }

        private String getUserProfileJson(AccountKind kind, int userId) throws SQLException {
            String sql = "SELECT first_name, last_name, username, email FROM " + kind.fromClause + " WHERE " + COL_USER_ID + " = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    String firstName = jsonEscape(rs.getString("first_name"));
                    String lastName = jsonEscape(rs.getString("last_name"));
                    String username = jsonEscape(rs.getString("username"));
                    String email = jsonEscape(rs.getString("email"));
                    return "{\"success\":true,\"data\":{\"first_name\":\"" + firstName + "\",\"last_name\":\"" + lastName + "\",\"username\":\"" + username + "\",\"email\":\"" + email + "\"}}";
                }
            }
        }
    }

    static class UserProfileUpdateHandler implements HttpHandler {

        private final AccountKind accountKind;

        UserProfileUpdateHandler(AccountKind accountKind) {
            this.accountKind = accountKind;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonUtf8(exchange, 405, "{\"success\":false, \"message\":\"Method not allowed. Use POST with form body (application/x-www-form-urlencoded).\"}");
                return;
            }

            InputStream is = exchange.getRequestBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            Map<String, String> params = parseForm(body.toString());
            String userIdRaw = trimValue(params.get("user_id"));
            String firstName = trimValue(params.get("first_name"));
            String lastName = trimValue(params.get("last_name"));
            String username = trimValue(params.get("username"));
            String email = trimValue(params.get("email"));

            if (isBlank(userIdRaw) || isBlank(firstName) || isBlank(lastName) || isBlank(username) || isBlank(email)) {
                sendJsonUtf8(exchange, 400, "{\"success\":false, \"message\":\"user_id, first_name, last_name, username, and email are required\"}");
                return;
            }

            int userId;
            try {
                userId = Integer.parseInt(userIdRaw);
            } catch (NumberFormatException e) {
                sendJsonUtf8(exchange, 400, "{\"success\":false, \"message\":\"user_id must be a number\"}");
                return;
            }

            if (email.indexOf('@') < 0) {
                sendJsonUtf8(exchange, 400, "{\"success\":false, \"message\":\"Invalid email address\"}");
                return;
            }

            try {
                if (!userExists(accountKind, userId)) {
                    sendJsonUtf8(exchange, 404, "{\"success\":false, \"message\":\"User not found\"}");
                    return;
                }
                if (isUsernameOrEmailTakenByOther(accountKind, username, email, userId)) {
                    sendJsonUtf8(exchange, 409, "{\"success\":false, \"message\":\"Username or email is already in use\"}");
                    return;
                }

                int rows = updateProfile(accountKind, userId, firstName, lastName, username, email);
                if (rows == 0) {
                    sendJsonUtf8(exchange, 404, "{\"success\":false, \"message\":\"User not found\"}");
                    return;
                }
                sendJsonUtf8(exchange, 200, "{\"success\":true, \"message\":\"Profile updated successfully\"}");
            } catch (SQLException e) {
                e.printStackTrace();
                sendJsonUtf8(exchange, 500, "{\"success\":false, \"message\":\"Profile update unavailable. Check database connection.\"}");
            }
        }

        private Map<String, String> parseForm(String data) {
            Map<String, String> params = new HashMap<>();
            if (data == null || data.isEmpty()) return params;
            for (String pair : data.split("&")) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        params.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name()),
                                URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return params;
        }

        private String trimValue(String value) {
            return value == null ? null : value.trim();
        }

        private boolean isBlank(String value) {
            return value == null || value.isEmpty();
        }

        private void sendJsonUtf8(HttpExchange exchange, int statusCode, String json) throws IOException {
            byte[] bodyBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bodyBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bodyBytes);
            os.close();
        }

        private boolean userExists(AccountKind kind, int userId) throws SQLException {
            String sql = "SELECT 1 FROM " + kind.fromClause + " WHERE " + COL_USER_ID + " = ? LIMIT 1";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        }

        private boolean isUsernameOrEmailTakenByOther(AccountKind kind, String username, String email, int userId) throws SQLException {
            String sql = "SELECT 1 FROM " + kind.fromClause + " WHERE (username = ? OR email = ?) AND " + COL_USER_ID + " <> ? LIMIT 1";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, email);
                stmt.setInt(3, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        }

        private int updateProfile(AccountKind kind, int userId, String firstName, String lastName, String username, String email) throws SQLException {
            String sql = "UPDATE " + kind.fromClause + " SET first_name = ?, last_name = ?, username = ?, email = ?, updated_by = ? WHERE " + COL_USER_ID + " = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, firstName);
                stmt.setString(2, lastName);
                stmt.setString(3, username);
                stmt.setString(4, email);
                stmt.setInt(5, userId);
                stmt.setInt(6, userId);
                return stmt.executeUpdate();
            }
        }
    }

    static class ChangePasswordHandler implements HttpHandler {

        private static final Pattern HAS_UPPER = Pattern.compile("[A-Z]");
        private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
        private static final Pattern HAS_SPECIAL = Pattern.compile("[@#!%_]");

        private final AccountKind accountKind;

        ChangePasswordHandler(AccountKind accountKind) {
            this.accountKind = accountKind;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonUtf8(exchange, 405, "{\"success\":false, \"message\":\"Method not allowed. Use POST with form body (application/x-www-form-urlencoded).\"}");
                return;
            }

            InputStream is = exchange.getRequestBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            Map<String, String> params = parseForm(body.toString());
            String userIdRaw = trimValue(params.get("user_id"));
            String currentPassword = params.get("current_password");
            String newPassword = params.get("new_password");

            if (currentPassword != null) {
                currentPassword = currentPassword.trim();
            }
            if (newPassword != null) {
                newPassword = newPassword.trim();
            }

            if (isBlank(userIdRaw) || isBlank(currentPassword) || isBlank(newPassword)) {
                sendJsonUtf8(exchange, 400, "{\"success\":false, \"message\":\"user_id, current_password, and new_password are required\"}");
                return;
            }

            int userId;
            try {
                userId = Integer.parseInt(userIdRaw);
            } catch (NumberFormatException e) {
                sendJsonUtf8(exchange, 400, "{\"success\":false, \"message\":\"user_id must be a number\"}");
                return;
            }

            if (!isStrongPassword(newPassword)) {
                sendJsonUtf8(exchange, 400, "{\"success\":false, \"message\":\"Password must be at least 8 characters and include an uppercase letter, a number, and a special character (@, #, !, %, or _).\"}");
                return;
            }

            try {
                String currentHash = toMd5IfNeeded(currentPassword);
                String storedHash = getStoredPasswordHash(accountKind, userId);
                if (storedHash == null) {
                    sendJsonUtf8(exchange, 404, "{\"success\":false, \"message\":\"User not found\"}");
                    return;
                }
                if (!storedHash.equalsIgnoreCase(currentHash)) {
                    sendJsonUtf8(exchange, 401, "{\"success\":false, \"message\":\"Current password does not match our records.\"}");
                    return;
                }

                String newHash = md5Hash(newPassword);
                int updated = updatePassword(accountKind, userId, newHash);
                if (updated == 0) {
                    sendJsonUtf8(exchange, 404, "{\"success\":false, \"message\":\"User not found\"}");
                    return;
                }
                sendJsonUtf8(exchange, 200, "{\"success\":true, \"message\":\"Password changed successfully.\"}");
            } catch (SQLException e) {
                e.printStackTrace();
                String hint = jdbcHint(e);
                sendJsonUtf8(exchange, 500,
                        "{\"success\":false, \"message\":\"" + jsonEscape(hint) + "\"}");
            }
        }

        private String jsonEscape(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private boolean isStrongPassword(String password) {
            if (password == null || password.length() < 8) {
                return false;
            }
            return HAS_UPPER.matcher(password).find()
                    && HAS_DIGIT.matcher(password).find()
                    && HAS_SPECIAL.matcher(password).find();
        }

        private Map<String, String> parseForm(String data) {
            Map<String, String> params = new HashMap<>();
            if (data == null || data.isEmpty()) return params;
            for (String pair : data.split("&")) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        params.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name()),
                                URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return params;
        }

        private String trimValue(String value) {
            return value == null ? null : value.trim();
        }

        private boolean isBlank(String value) {
            return value == null || value.isEmpty();
        }

        private void sendJsonUtf8(HttpExchange exchange, int statusCode, String json) throws IOException {
            byte[] bodyBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bodyBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bodyBytes);
            os.close();
        }

        private String getStoredPasswordHash(AccountKind kind, int userId) throws SQLException {
            String sql = "SELECT password FROM " + kind.fromClause + " WHERE " + COL_USER_ID + " = ? LIMIT 1";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return rs.getString("password");
                }
            }
        }

        private int updatePassword(AccountKind kind, int userId, String hashedPassword) throws SQLException {
            String sql = "UPDATE " + kind.fromClause + " SET password = ? WHERE " + COL_USER_ID + " = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, hashedPassword);
                stmt.setInt(2, userId);
                return stmt.executeUpdate();
            }
        }
    }

    static class DeleteAccountHandler implements HttpHandler {

        private final AccountKind accountKind;

        DeleteAccountHandler(AccountKind accountKind) {
            this.accountKind = accountKind;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonUtf8(exchange, 405, "{\"success\":false, \"message\":\"Method not allowed. Use POST with form body (application/x-www-form-urlencoded).\"}");
                return;
            }

            InputStream is = exchange.getRequestBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            Map<String, String> params = parseForm(body.toString());
            String userIdRaw = trimValue(params.get("user_id"));
            String password = params.get("password");
            if (password != null) {
                password = password.trim();
            }

            if (isBlank(userIdRaw) || isBlank(password)) {
                sendJsonUtf8(exchange, 400, "{\"success\":false, \"message\":\"user_id and password are required\"}");
                return;
            }

            int userId;
            try {
                userId = Integer.parseInt(userIdRaw);
            } catch (NumberFormatException e) {
                sendJsonUtf8(exchange, 400, "{\"success\":false, \"message\":\"user_id must be a number\"}");
                return;
            }

            try {
                String storedHash = getStoredPasswordHash(accountKind, userId);
                if (storedHash == null) {
                    sendJsonUtf8(exchange, 404, "{\"success\":false, \"message\":\"User not found\"}");
                    return;
                }

                String submittedHash = toMd5IfNeeded(password);
                if (!storedHash.equalsIgnoreCase(submittedHash)) {
                    sendJsonUtf8(exchange, 401, "{\"success\":false, \"message\":\"Password does not match our records.\"}");
                    return;
                }

                int deleted = deleteUser(accountKind, userId);
                if (deleted == 0) {
                    sendJsonUtf8(exchange, 404, "{\"success\":false, \"message\":\"User not found\"}");
                    return;
                }

                sendJsonUtf8(exchange, 200, "{\"success\":true, \"message\":\"Account deleted successfully.\"}");
            } catch (SQLException e) {
                e.printStackTrace();
                String hint = jdbcHint(e);
                sendJsonUtf8(exchange, 500,
                        "{\"success\":false, \"message\":\"" + jsonEscape(hint) + "\"}");
            }
        }

        private Map<String, String> parseForm(String data) {
            Map<String, String> params = new HashMap<>();
            if (data == null || data.isEmpty()) return params;
            for (String pair : data.split("&")) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        params.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name()),
                                URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return params;
        }

        private String trimValue(String value) {
            return value == null ? null : value.trim();
        }

        private boolean isBlank(String value) {
            return value == null || value.isEmpty();
        }

        private String jsonEscape(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private void sendJsonUtf8(HttpExchange exchange, int statusCode, String json) throws IOException {
            byte[] bodyBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bodyBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bodyBytes);
            os.close();
        }

        private String getStoredPasswordHash(AccountKind kind, int userId) throws SQLException {
            String sql = "SELECT password FROM " + kind.fromClause + " WHERE " + COL_USER_ID + " = ? LIMIT 1";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return rs.getString("password");
                }
            }
        }

        private int deleteUser(AccountKind kind, int userId) throws SQLException {
            String sql = "DELETE FROM " + kind.fromClause + " WHERE " + COL_USER_ID + " = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                return stmt.executeUpdate();
            }
        }
    }
}
