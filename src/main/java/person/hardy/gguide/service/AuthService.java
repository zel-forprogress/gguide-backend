package person.hardy.gguide.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import person.hardy.gguide.common.util.JWTUtil;
import person.hardy.gguide.model.dto.UserProfileDTO;
import person.hardy.gguide.model.entity.User;
import person.hardy.gguide.repository.UserRepository;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JWTUtil jwtUtil;

    public User register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setAdmin(false);

        return userRepository.save(newUser);
    }

    public String login(String username, String password) {
        User user = findUser(username);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return jwtUtil.generateToken(username);
    }

    public UserProfileDTO getCurrentUser(String username) {
        User user = findUser(username);
        return new UserProfileDTO(user.getUsername(), resolveAdmin(user));
    }

    public boolean isAdmin(String username) {
        return resolveAdmin(findUser(username));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private boolean resolveAdmin(User user) {
        if (user.isAdmin()) {
            return true;
        }

        // Keep the existing development admin account usable after adding the new field.
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            user.setAdmin(true);
            userRepository.save(user);
            return true;
        }

        return false;
    }
}
