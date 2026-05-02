package person.hardy.gguide.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import person.hardy.gguide.common.util.JWTUtil;
import person.hardy.gguide.model.dto.UserProfileDTO;
import person.hardy.gguide.model.entity.User;
import person.hardy.gguide.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class AuthService {

    private static final int MAX_AVATAR_URL_LENGTH = 1_500_000;
    private static final String[] DEFAULT_AVATAR_COLORS = {
            "#ff5e4d", "#8f5cff", "#2fb981", "#3f7cff", "#f2a93b", "#d94f86"
    };

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
        newUser.setAvatarUrl(createDefaultAvatarUrl(username));

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
        String avatarUrl = resolveAvatarUrl(user);
        return new UserProfileDTO(user.getUsername(), resolveAdmin(user), avatarUrl);
    }

    public boolean isAdmin(String username) {
        return resolveAdmin(findUser(username));
    }

    public UserProfileDTO updateAvatar(String username, String avatarUrl) {
        User user = findUser(username);
        user.setAvatarUrl(normalizeAvatarUrl(user.getUsername(), avatarUrl));
        User saved = userRepository.save(user);
        return new UserProfileDTO(saved.getUsername(), resolveAdmin(saved), saved.getAvatarUrl());
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

    private String resolveAvatarUrl(User user) {
        String avatarUrl = normalizeAvatarUrl(user.getUsername(), user.getAvatarUrl());
        if (!avatarUrl.equals(user.getAvatarUrl())) {
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);
        }
        return avatarUrl;
    }

    private String normalizeAvatarUrl(String username, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return createDefaultAvatarUrl(username);
        }

        if (avatarUrl.length() > MAX_AVATAR_URL_LENGTH) {
            throw new RuntimeException("Avatar image is too large");
        }

        if (!avatarUrl.startsWith("data:image/png;base64,")
                && !avatarUrl.startsWith("data:image/jpeg;base64,")
                && !avatarUrl.startsWith("data:image/webp;base64,")
                && !avatarUrl.startsWith("data:image/gif;base64,")
                && !avatarUrl.startsWith("data:image/svg+xml;base64,")) {
            throw new RuntimeException("Unsupported avatar image");
        }

        return avatarUrl;
    }

    private String createDefaultAvatarUrl(String username) {
        String safeUsername = username == null || username.isBlank() ? "U" : username.trim();
        String initial = escapeXml(safeUsername.substring(0, 1).toUpperCase());
        int index = Math.floorMod(safeUsername.hashCode(), DEFAULT_AVATAR_COLORS.length);
        String color = DEFAULT_AVATAR_COLORS[index];
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"160\" height=\"160\" viewBox=\"0 0 160 160\">"
                + "<rect width=\"160\" height=\"160\" rx=\"44\" fill=\"" + color + "\"/>"
                + "<circle cx=\"128\" cy=\"34\" r=\"52\" fill=\"rgba(255,255,255,0.22)\"/>"
                + "<circle cx=\"24\" cy=\"132\" r=\"48\" fill=\"rgba(0,0,0,0.12)\"/>"
                + "<text x=\"50%\" y=\"55%\" text-anchor=\"middle\" dominant-baseline=\"middle\" "
                + "font-family=\"Arial, sans-serif\" font-size=\"72\" font-weight=\"800\" fill=\"#fff\">"
                + initial
                + "</text></svg>";
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
