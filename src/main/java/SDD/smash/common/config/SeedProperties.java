package SDD.smash.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "seed")
@Getter
@Setter
public class SeedProperties {
    private String version;
}
