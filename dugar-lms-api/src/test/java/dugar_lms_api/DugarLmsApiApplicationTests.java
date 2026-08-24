package dugar_lms_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"spring.autoconfigure.exclude=org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
	"spring.datasource.url=jdbc:postgresql://127.0.0.1:5434/dugar_lms",
	"spring.datasource.username=dugar_lms_user",
	"spring.datasource.password=mysecret"
})
class DugarLmsApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
