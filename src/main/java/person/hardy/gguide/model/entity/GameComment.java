package person.hardy.gguide.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "game_comments")
public class GameComment {

    @Id
    private String id;

    @Indexed
    private String gameId;

    @Indexed
    private String username;

    private String avatarUrl;

    private String content;

    private Instant createdAt = Instant.now();

    private Instant updatedAt;
}
