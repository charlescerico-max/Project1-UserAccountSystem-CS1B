package service;
import model.User;
import java.util.ArrayList;

public class Userservice {
    private ArrayList<User> users = new ArrayList<>();

    // Register a new user
    public void register(User user) {
        users.add(user);
        System.out.println("User registered: " + user.getUsername());
    }

    // Login check
    public boolean login(String username, String password) {
        for (User u : users) {
            if (u.getUsername().equals(username) && u.getPassword().equals(password)) {
                return true;
            }
        }
        return false;
    }
}
