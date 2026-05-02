package person.hardy.gguide.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AISettingsDTO {
    private boolean configured;
    private String apiKeyPreview;
    private String baseUrl;
    private String model;
    private boolean usingDefaultBaseUrl;
    private boolean usingDefaultModel;
}
