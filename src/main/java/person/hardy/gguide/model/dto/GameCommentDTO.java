package person.hardy.gguide.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class GameCommentDTO {
    private String id;
    private String gameId;
    private String username;
    private String avatarUrl;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;
}
