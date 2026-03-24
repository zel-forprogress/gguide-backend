package person.hardy.gguide;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"app.seed.enabled=false",
	"spring.data.mongodb.auto-index-creation=false"
})
class GguideApplicationTests {

	@Test
	void contextLoads() {
	}

}
