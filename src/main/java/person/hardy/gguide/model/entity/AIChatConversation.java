package person.hardy.gguide.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import person.hardy.gguide.model.dto.ChatMessageDTO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "ai_chat_conversations")
public class AIChatConversation {

    @Id
    private String id;

    @Indexed
    private String username;

    private String title;

    private List<ChatMessageDTO> messages = new ArrayList<>();

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();
}
