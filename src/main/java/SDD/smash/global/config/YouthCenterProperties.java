package SDD.smash.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="apis.youthcenter")
@Getter
@Setter
public class YouthCenterProperties {

    private String baseUrl;
    private String path;
    private String apiKey;
}
