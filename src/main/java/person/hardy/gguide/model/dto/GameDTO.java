package person.hardy.gguide.model.dto;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class GameDTO {
    private String id;
    private String title;
    private String description;
    private String coverImage;
    private Double rating;
    private List<String> categories = new ArrayList<>();
    private Instant releaseDate;
    private String cinematicTrailer;
    private String downloadLink;
}
