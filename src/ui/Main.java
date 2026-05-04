package ui;
import service.Userservice;
import model.User;

public class Main {
    public static void main(String[] args) {
        Userservice service = new Userservice();

        // Register a user
        User user1 = new User("france", "1234", "france@email.com");
        service.register(user1);

        // Attempt login
        boolean success = service.login("france", "1234");
        System.out.println(success ? "Login successful!" : "Login failed.");
    }
}
