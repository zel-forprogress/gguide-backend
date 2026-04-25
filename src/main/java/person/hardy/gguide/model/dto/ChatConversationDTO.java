package person.hardy.gguide.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationDTO {
    private String id;
    private String title;
    private List<ChatMessageDTO> messages;
    private Instant createdAt;
    private Instant updatedAt;
}
