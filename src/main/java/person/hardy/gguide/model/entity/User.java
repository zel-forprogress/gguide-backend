package person.hardy.gguide.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data // Lombok: 自动生成 Getters, Setters, toString, 等方法
@Document(collection = "users") // 映射到 MongoDB 的 "users" 集合
public class User {

    @Id
    private String id;

    @Indexed(unique = true) // 声明这是个唯一索引
    private String username;

    private String password;

    private List<String> favoriteGameIds = new ArrayList<>();

    private Instant createdAt = Instant.now();
}
