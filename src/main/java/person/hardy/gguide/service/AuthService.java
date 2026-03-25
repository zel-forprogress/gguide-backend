package person.hardy.gguide.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import person.hardy.gguide.common.util.JWTUtil;
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

    //注册
    public User register(String username, String password) {
        // 1. 检查用户名是否已存在
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. 创建新用户并加密密码
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password)); // 加密！

        // 3. 保存到数据库
        return userRepository.save(newUser);
    }

    //登录
    public String login(String username, String password) {
        // 1. 查找用户
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 2. 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 3. 登录成功，返回token
        return jwtUtil.generateToken(username);
    }
}
