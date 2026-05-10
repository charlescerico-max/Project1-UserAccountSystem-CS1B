package main.java;
import java.sql.*;
import javax.swing.*;

public class App {

    public static void main(String[] args) throws Exception {

        String url = "jdbc:mysql://localhost:3306/accsystem_db?serverTimezone=UTC";
        String user = "root";
        String password = "";

        try {
            Connection conn = DriverManager.getConnection(url, user, password);

            // If successful, show a popup!
            JOptionPane.showMessageDialog(
                null,
                "Database Connected Successfully!",
                "Connection Status",
                JOptionPane.INFORMATION_MESSAGE
            );

            conn.close();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(
                null,
                "Connection Failed!\n\n" + e.getMessage(),
                "Connection Status",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}