package person.hardy.gguide.model.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class GameDTO {
    private String id;
    private String title;
    private String description;
    private String coverImage;
    private Double rating;
    private String category;
    private Instant releaseDate;
    private String cinematicTrailer;//游戏CG
    private String downloadLink;//或者官网连接
}
