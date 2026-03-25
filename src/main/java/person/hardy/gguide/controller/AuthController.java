package person.hardy.gguide.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import person.hardy.gguide.model.dto.AuthRequestDTO;
import person.hardy.gguide.model.entity.User;
import person.hardy.gguide.model.vo.ResultVO;
import person.hardy.gguide.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth") // 所有请求都以 /api/auth 开头
@CrossOrigin(origins = "*") // 允许所有跨域请求，开发时方便，生产环境需谨慎配置
public class AuthController {

    @Autowired
    private AuthService authService;


    @PostMapping("/register")
    public ResultVO<?> registerUser(@RequestBody AuthRequestDTO request) {
        User user = authService.register(request.getUsername(), request.getPassword());
        return ResultVO.success("注册成功", user.getId());
    }

    @PostMapping("/login")
    public ResultVO<?> loginUser(@RequestBody AuthRequestDTO request) {
        String token = authService.login(request.getUsername(), request.getPassword());
        return ResultVO.success("登录成功", Map.of("token", token));
    }
}