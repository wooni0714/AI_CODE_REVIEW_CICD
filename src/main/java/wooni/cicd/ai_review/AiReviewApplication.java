package wooni.cicd.ai_review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiReviewApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiReviewApplication.class, args);
	}

}
