import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Application {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/accsystem_db?serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

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

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/hello", new HelloHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/user/login", new LoginHandler());
        server.createContext("/admin/login", new LoginHandler());
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
                // Check against database
                String hashedPassword = toMd5IfNeeded(password);
                Integer userId = authenticateUser(username, hashedPassword);
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

        private Integer authenticateUser(String username, String password) throws SQLException {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                System.out.println("Database connection established");

                String sql = "SELECT user_id FROM users WHERE (username = ? OR email = ?) AND password = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, username);
                stmt.setString(2, username);
                stmt.setString(3, password);
                System.out.println("Executing fallback query with username=" + username + " and password=" + password);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int userId = rs.getInt("user_id");
                    System.out.println("User authenticated with user_id: " + userId);
                    return userId;
                } else {
                    System.out.println("No user found with user_id query");
                }
            }
            return null;
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
            String sql = "SELECT 1 FROM users WHERE username = ? OR email = ? LIMIT 1";
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
            String sql = "INSERT INTO users (first_name, last_name, username, email, password, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)";
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
}