package org.chappyGolf.config;

import com.squareup.square.SquareClient;
import com.squareup.square.SquareClientBuilder;
import com.squareup.square.core.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SquareConfig {

    @Value("${square.access.token}")
    private String accessToken;

    @Bean
    public SquareClient squareClient() {
        return new SquareClientBuilder()
                .environment(Environment.SANDBOX) // use PRODUCTION later
                .token(accessToken)
                .build();
    }
}