package person.hardy .gguide.model.vo;

import lombok.Data;

@Data
public class AuthResponseVO {
    private String message;
    private String userId;
    private String token;

    // 构造方法
    public static AuthResponseVO success(String message, String userId) {
        AuthResponseVO vo = new AuthResponseVO();
        vo.setMessage(message);
        vo.setUserId(userId);
        return vo;
    }

    public static AuthResponseVO loginSuccess(String message, String token) {
        AuthResponseVO vo = new AuthResponseVO();
        vo.setMessage(message);
        vo.setToken(token);
        return vo;
    }
}
