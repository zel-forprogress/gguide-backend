package person.hardy.gguide.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import person.hardy.gguide.model.entity.Game;

import java.util.List;

@Repository
public interface GameRepository extends MongoRepository<Game, String> {
    List<Game> findByCategory(String category);

    List<Game> findByTitleContainingIgnoreCase(String title);
}
