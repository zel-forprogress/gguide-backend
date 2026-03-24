package person.hardy.gguide.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import person.hardy.gguide.model.dto.AuthRequestDTO;
import person.hardy.gguide.model.entity.User;
import person.hardy.gguide.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth") // 所有请求都以 /api/auth 开头
@CrossOrigin(origins = "*") // 允许所有跨域请求，开发时方便，生产环境需谨慎配置
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody AuthRequestDTO request) {
        try {
            User user = authService.register(request.getUsername(), request.getPassword());
            // 实际项目中，这里应该生成并返回一个 JWT Token
            return ResponseEntity.ok(Map.of("message", "注册成功", "userId", user.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody AuthRequestDTO request) {
        try {
            User user = authService.login(request.getUsername(), request.getPassword());
            // 实际项目中，这里应该生成并返回一个 JWT Token
            return ResponseEntity.ok(Map.of("message", "登录成功", "token", "fake-jwt-token-for-" + user.getUsername()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }
}