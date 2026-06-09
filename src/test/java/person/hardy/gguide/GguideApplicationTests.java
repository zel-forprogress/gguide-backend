package person.hardy.gguide;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import person.hardy.gguide.repository.AIChatConversationRepository;
import person.hardy.gguide.repository.GameCommentRepository;
import person.hardy.gguide.repository.GameRepository;
import person.hardy.gguide.repository.UserRepository;

@SpringBootTest(properties = {
	"app.seed.enabled=false",
	"spring.data.mongodb.auto-index-creation=false",
	"spring.autoconfigure.exclude=org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
			+ "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration,"
			+ "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration",
	"jwt.secret=01234567890123456789012345678901",
	"jwt.expiration-ms=86400000"
})
class GguideApplicationTests {

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private GameRepository gameRepository;

	@MockitoBean
	private GameCommentRepository gameCommentRepository;

	@MockitoBean
	private AIChatConversationRepository aiChatConversationRepository;

	@Test
	void contextLoads() {
	}

}
