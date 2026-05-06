package person.hardy.gguide.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import person.hardy.gguide.model.entity.GameComment;

import java.util.List;

@Repository
public interface GameCommentRepository extends MongoRepository<GameComment, String> {
    List<GameComment> findByGameIdOrderByCreatedAtAsc(String gameId);
}
