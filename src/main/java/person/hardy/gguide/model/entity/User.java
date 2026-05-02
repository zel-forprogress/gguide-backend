package person.hardy.gguide.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String password;

    private boolean admin = false;

    private String avatarUrl;

    private String aiApiKey;

    private String aiBaseUrl;

    private String aiModel;

    private List<String> favoriteGameIds = new ArrayList<>();

    private List<String> recentlyViewedGameIds = new ArrayList<>();

    private Instant createdAt = Instant.now();
}
