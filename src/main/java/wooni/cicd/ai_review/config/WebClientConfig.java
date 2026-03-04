package wooni.cicd.ai_review.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {
    private static final String GITHUB_API_URL = "https://api.github.com";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com";

    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 60;
    private static final int WRITE_TIMEOUT_SECONDS = 10;

    private final ReviewProperties reviewProperties;

    @Bean
    public WebClient githubWebClient() {
        return WebClient.builder()
                .baseUrl(GITHUB_API_URL)
                .defaultHeader("Authorization", "Bearer " + reviewProperties.githubToken())
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient()))
                .build();
    }

    @Bean
    public WebClient geminiWebClient() {
        return WebClient.builder()
                .baseUrl(GEMINI_API_URL)
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient()))
                .build();
    }

    private HttpClient buildHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_SECONDS * 1000)
                .responseTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                );
    }
}
