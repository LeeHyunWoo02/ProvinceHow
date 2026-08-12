package SDD.smash.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(YouthCenterProperties.class)
public class YouthCenterConfig {

    @Bean
    public WebClient youthCenterWebClient(YouthCenterProperties youthCenterProperties)
    {
        return WebClient.builder()
                .baseUrl(youthCenterProperties.getBaseUrl())
                .build();
    }
}
