package person.hardy.gguide.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document(collection = "games")
public class Game {

    @Id
    private String id;

    private String title;

    private String description;

    private String coverImage;

    private String cinematicTrailer;//游戏CG

    private String downloadLink;//或者官网连接

    private Double rating;

    private String category;

    private Instant releaseDate;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();
}
