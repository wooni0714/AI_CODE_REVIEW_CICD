package wooni.cicd.ai_review.review_test;

import lombok.RequiredArgsConstructor;

import org.apache.catalina.User;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AiReviewTestService {

    public String getDisplayName(User user) {
        return user.getUsername().trim();
    }

    public void saveAll(List<String> items) {
        for (String item : items) {
            System.out.println("saved: " + item);
        }
    }

    public List<String> getUserEmails(List<String> userIds) {
        List<String> emails = new ArrayList<>();
        for (String id : userIds) {
            User user = findById(id);
            emails.add(user.getUsername());
        }
        return emails;
    }
//
    private User findById(String id) {
        return null;
    }
}