package person.hardy.gguide.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import person.hardy.gguide.model.dto.AuthRequestDTO;
import person.hardy.gguide.model.dto.UserProfileDTO;
import person.hardy.gguide.model.entity.User;
import person.hardy.gguide.model.vo.ResultVO;
import person.hardy.gguide.service.AuthService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResultVO<?> registerUser(@RequestBody AuthRequestDTO request) {
        User user = authService.register(request.getUsername(), request.getPassword());
        return ResultVO.success("Register success", user.getId());
    }

    @PostMapping("/login")
    public ResultVO<?> loginUser(@RequestBody AuthRequestDTO request) {
        String token = authService.login(request.getUsername(), request.getPassword());
        return ResultVO.success("Login success", Map.of("token", token));
    }

    @GetMapping("/me")
    public ResultVO<UserProfileDTO> getCurrentUser(Principal principal) {
        return ResultVO.success(authService.getCurrentUser(principal.getName()));
    }
}
