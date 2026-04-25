package person.hardy.gguide.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import person.hardy.gguide.model.entity.AIChatConversation;

import java.util.List;

@Repository
public interface AIChatConversationRepository extends MongoRepository<AIChatConversation, String> {
    List<AIChatConversation> findTop30ByUsernameOrderByUpdatedAtDesc(String username);
}
