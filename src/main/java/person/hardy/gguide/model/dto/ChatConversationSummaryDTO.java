package person.hardy.gguide.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationSummaryDTO {
    private String id;
    private String title;
    private Instant updatedAt;
    private int messageCount;
}
