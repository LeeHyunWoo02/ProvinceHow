package SDD.smash.global.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;


@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final Environment env;

    @Value("${front_url}")
    private String[] frontUrl;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        if(isDevProfileActive())
        {
            http
                    .authorizeHttpRequests((auth) -> auth
                            .anyRequest().permitAll()
                    );
        }
        else
        {
            http
                    .authorizeHttpRequests((auth) -> auth
                            .requestMatchers("/api/**").permitAll()
                            // 액추에이터(health/prometheus)를 허용한다. 관리 포트를 따로 뒀어도
                            // 이 필터체인은 관리 포트에도 적용되므로, 허용하지 않으면 Prometheus
                            // 스크랩이 403 으로 막힌다(ActuatorPrometheusIntegrationTest 가 검증).
                            //
                            // EndpointRequest.toAnyEndpoint() 는 쓸 수 없다. 관리 포트를 분리하면
                            // PathMappedEndpoints 빈이 자식(관리) 컨텍스트에만 생겨서, 이 체인이
                            // 사는 메인 컨텍스트에서는 매처가 항상 "무시"로 빠져 403 이 된다.
                            //
                            // 경로를 하나씩 적는다 - 나중에 exposure.include 에 env/beans 를 추가해도
                            // 자동으로 열리지 않게 하려는 의도다. 외부 차단은 compose 가 관리 포트를
                            // publish 하지 않는 것으로 한다.
                            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                            .anyRequest().authenticated() //기본 거부 정책 적용
                    );
        }

        /**
         * cors 관련 설정
         * */
        http
                .cors((cors) -> cors
                        .configurationSource(new CorsConfigurationSource() {
                            @Override
                            public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                                CorsConfiguration config = new CorsConfiguration();

                                List<String> allowed = Arrays.asList(frontUrl);
                                config.setAllowedOrigins(allowed);
                                config.setAllowedMethods(List.of("GET", "OPTIONS")); // GET, OPTIONS(프리플라이트)만 허용
                                config.setAllowCredentials(false); // 비회원 + 쿠기 사용 안함
                                config.setAllowedHeaders(List.of("Content-Type", "Accept"));
                                config.setMaxAge(3600L);

                                return config;
                            }
                        }));
        http
                .formLogin((formLogin) -> formLogin.disable());

        return http.build();
    }

    private boolean isDevProfileActive() {
        return Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("dev"));
    }
}
