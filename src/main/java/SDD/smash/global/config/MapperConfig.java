package SDD.smash.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfig {
    @Bean
    public ObjectMapper objectMapper() { // JSON
        return new ObjectMapper();
    }

    @Bean
    public XmlMapper xmlMapper() {       // XML → JSON 변환
        return new XmlMapper();
    }
}
