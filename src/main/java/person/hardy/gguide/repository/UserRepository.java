package person.hardy.gguide.repository;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import person.hardy.gguide.model.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    // Spring Data 会自动根据方法名实现这个查询
    Optional<User> findByUsername(String username);
}