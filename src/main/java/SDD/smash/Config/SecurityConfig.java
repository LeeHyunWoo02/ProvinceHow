package SDD.smash.Config;

import SDD.smash.Security.Filter.ApiRateLimitFilter;
import SDD.smash.Security.Service.ApiRateLimitService;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;


@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiRateLimitService apiRateLimitService;
    private final Environment env;

    @Value("${front_url}")
    private String[] frontUrl;

    @Value("${ratelimit.secret}")
    private String ipSecret;

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


//        http
//                .addFilterBefore(new ApiRateLimitFilter(apiRateLimitService, env, ipSecret), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private boolean isDevProfileActive() {
        return Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("dev"));
    }
}
