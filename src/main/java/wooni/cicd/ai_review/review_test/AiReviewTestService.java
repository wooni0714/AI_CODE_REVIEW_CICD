package wooni.cicd.ai_review.review_test;

import org.apache.catalina.User;

import java.util.List;

public class AiReviewTestService {

    public String getDisplayName(User user) {
        return user.getUsername().trim();
    }

    public void saveAll(List<String> items) {
        for (String item : items) {
            System.out.println("saved: " + item);
        }
    }
}