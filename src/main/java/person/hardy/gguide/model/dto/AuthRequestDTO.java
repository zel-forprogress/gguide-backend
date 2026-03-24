package person.hardy.gguide.model.dto;

import lombok.Data;

@Data
public class AuthRequestDTO {
    private String username;
    private String password;
}