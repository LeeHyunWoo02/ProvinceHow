package SDD.smash;

import SDD.smash.domain.support.infrastructure.scheduler.SupportPolicyRefreshScheduler;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;

/**
 * 실제 MySQL 이 필요한 통합 테스트의 공통 베이스.
 * Docker 데몬이 떠 있어야 한다. Testcontainers 가 mysql:8.0 이미지를 직접 받아 쓴다.
 *
 * 컨테이너는 static 초기화로 한 번만 띄우고 JVM 종료 시까지 재사용한다(싱글턴 컨테이너 패턴).
 * 테스트에서는 data / meta 를 같은 스키마로 둔다. 운영은 두 스키마로 분리돼 있지만,
 * 분리 자체를 검증하는 테스트가 아니라면 컨테이너 하나로 충분하다.
 */
@SpringBootTest
public abstract class IntegrationTestSupport {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("smash_data")
            .withCommand(
                    "mysqld",
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--default-time-zone=+09:00"
            );

    static {
        MYSQL.start();
    }

    /*
     * 기동 시 배치를 돌리는 경로는 SeedMasterJobLauncher 하나뿐이고,
     * src/test/resources/application.properties 의 seed.master.enabled=false 가 그것을 끈다.
     * 그래서 배치 러너를 대역으로 바꿀 필요가 없다.
     */

    /**
     * @Scheduled(initialDelay = 0) 이라 컨텍스트가 뜨자마자 청년정책 API 를 전 시군구에 대해 호출한다.
     * recommendation 단계에서 옛 {@code YouthSupportScheduler} 를 대체했다.
     */
    @MockitoBean
    private SupportPolicyRefreshScheduler supportPolicyRefreshScheduler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource-data.jdbc-url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource-data.username", MYSQL::getUsername);
        registry.add("spring.datasource-data.password", MYSQL::getPassword);

        registry.add("spring.datasource-meta.jdbc-url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource-meta.username", MYSQL::getUsername);
        registry.add("spring.datasource-meta.password", MYSQL::getPassword);
    }
}
