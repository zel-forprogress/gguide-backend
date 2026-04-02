package person.hardy.gguide.model.dto;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class GameDTO {
    private String id;
    private String title;
    private String description;
    private Map<String, String> titleI18n = new LinkedHashMap<>();
    private Map<String, String> descriptionI18n = new LinkedHashMap<>();
    private String coverImage;
    private Double rating;
    private List<String> categories = new ArrayList<>();
    private List<String> categoryLabels = new ArrayList<>();
    private Instant releaseDate;
    private String cinematicTrailer;
    private String downloadLink;
}
