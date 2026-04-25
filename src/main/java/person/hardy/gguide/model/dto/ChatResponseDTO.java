package person.hardy.gguide.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {
    private String conversationId;
    private String title;
    private String response;
    private List<ChatMessageDTO> messages;
    private Instant updatedAt;
}
