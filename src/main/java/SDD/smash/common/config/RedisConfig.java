package SDD.smash.common.config;

import SDD.smash.legacy.support.dto.SupportListDTO;
import SDD.smash.support.infrastructure.cache.SupportPolicyListPayload;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RedisConfig {
    @Bean
    public RestTemplate restTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));
        return new RestTemplate(factory);
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, SupportListDTO> supportListDTORedisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, SupportListDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);
        template.setKeySerializer(new StringRedisSerializer());

        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        var javaType = om.getTypeFactory().constructType(SupportListDTO.class);
        var serializer = new Jackson2JsonRedisSerializer<SupportListDTO>(javaType);

        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * support 컨텍스트 새 정본 저장소({@code support:policy:*})의 목록 값 전용 템플릿.
     * redis-conventions §3.1 이 요구하는 {@code supportListRedisTemplate} 이다.
     * 옛 {@code supportListDTORedisTemplate}(위)과 별개의 제네릭 타입이라 공존해도
     * Spring 이 타입으로 구분해 주입한다.
     */
    @Bean
    public RedisTemplate<String, SupportPolicyListPayload> supportListRedisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, SupportPolicyListPayload> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);
        template.setKeySerializer(new StringRedisSerializer());

        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        var javaType = om.getTypeFactory().constructType(SupportPolicyListPayload.class);
        var serializer = new Jackson2JsonRedisSerializer<SupportPolicyListPayload>(javaType);

        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

}
