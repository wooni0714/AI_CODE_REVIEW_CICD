package wooni.cicd.ai_review.reviewTest;

import org.apache.catalina.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestService {

    public String getDisplayName(User user) {
        return user.getUsername().trim();
    }

    public void saveAll(List<String> items) {
        for (String item : items) {
            System.out.println("saved: " + item);
        }
    }
}
