package person.hardy.gguide.model.dto;

import lombok.Data;

@Data
public class AISettingsRequestDTO {
    private String apiKey;
    private String baseUrl;
    private String model;
    private boolean clearApiKey;
}
