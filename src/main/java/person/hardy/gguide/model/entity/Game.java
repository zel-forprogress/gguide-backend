package person.hardy.gguide.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "games")
public class Game {

    @Id
    private String id;

    private String title;

    private String description;

    private String coverImage;

    private String cinematicTrailer;

    private String downloadLink;

    private Double rating;

    private List<String> categories = new ArrayList<>();

    private Instant releaseDate;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();
}
