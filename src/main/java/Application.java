import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Application {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/accsystem_db?serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/hello", new HelloHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/user/login", new LoginHandler());
        server.createContext("/admin/login", new LoginHandler());
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
                Integer userId = authenticateUser(username, password);
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
}