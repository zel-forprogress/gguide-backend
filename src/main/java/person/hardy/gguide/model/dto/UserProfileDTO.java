package person.hardy.gguide.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserProfileDTO {
    private String username;
    private boolean admin;
    private String avatarUrl;
}
