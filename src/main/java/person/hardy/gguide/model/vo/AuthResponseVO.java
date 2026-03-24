package person.hardy .gguide.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponseVO {
    private String message;
    private String userId;
    private String token;

}
