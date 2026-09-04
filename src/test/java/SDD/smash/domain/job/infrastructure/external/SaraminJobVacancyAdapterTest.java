package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobVacancy;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.metrics.ExternalApiMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사람인 채용공고 목록 어댑터 테스트. 실제 API 를 부르지 않는다(MockWebServer).
 */
class SaraminJobVacancyAdapterTest {

    private static final String ACCESS_KEY = "TEST-ACCESS-KEY";

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("access-key 가 비어 있으면 호출하지 않고 '미시도'(Optional.empty)를 돌려준다")
    void returnsEmptyWithoutAccessKey() {
        SaraminJobVacancyAdapter adapter = adapter("", specWithRegionMapping(Map.of("101000", "11680")));

        Optional<List<JobVacancy>> result = adapter.findVacancies(SigunguCode.of("11680"), 5);

        assertThat(result).isEmpty();   // 미시도 → 유스케이스가 캐싱하지 않는다
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("지역 역매핑이 비어 있으면 호출하지 않고 '미시도'(Optional.empty)를 돌려준다")
    void returnsEmptyWhenRegionNotReverseMapped() {
        SaraminJobVacancyAdapter adapter = adapter(ACCESS_KEY, SaraminApiSpecFile.defaults());

        Optional<List<JobVacancy>> result = adapter.findVacancies(SigunguCode.of("11680"), 5);

        assertThat(result).isEmpty();
        assertThat(server.getRequestCount()).isZero();   // loc_cd 없이 전국을 부르지 않는다
    }

    @Test
    @DisplayName("역매핑이 있으면 loc_cd 로 조회하고 카드 필드를 채운다")
    void mapsVacanciesWhenReverseMappingPresent() throws InterruptedException {
        // given - 사람인 loc_cd 101000 ↔ 우리 시군구 11680
        SaraminJobVacancyAdapter adapter = adapter(ACCESS_KEY, specWithRegionMapping(Map.of("101000", "11680")));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json;charset=UTF-8")
                .setBody("""
                        {
                          "jobs": { "count": 1, "start": 0, "total": "1", "job": {
                            "id": "46203390",
                            "url": "https://saramin/1",
                            "active": 1,
                            "expiration-timestamp": "1756598400",
                            "company": { "detail": { "name": "스매시" } },
                            "position": {
                              "title": "백엔드 개발자",
                              "location": { "code": "101000", "name": "서울 > 강남구" },
                              "job-code": { "name": "웹개발" }
                            },
                            "salary": { "name": "회사내규에 따름" }
                          } }
                        }
                        """));

        // when
        List<JobVacancy> result = adapter.findVacancies(SigunguCode.of("11680"), 5).orElseThrow();

        // then
        assertThat(result).hasSize(1);
        JobVacancy v = result.get(0);
        assertThat(v.title()).isEqualTo("백엔드 개발자");
        assertThat(v.companyName()).isEqualTo("스매시");
        assertThat(v.regionName()).isEqualTo("서울 > 강남구");
        assertThat(v.salaryText()).isEqualTo("회사내규에 따름");
        assertThat(v.active()).isTrue();
        assertThat(v.expirationDate()).isNotNull();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath())
                .contains("/job-search")
                .contains("access-key=" + ACCESS_KEY)
                .contains("loc_cd=101000")
                .contains("count=5")
                .contains("start=0");
        // RestClient 는 Accept 를 자동으로 채우지 않는다. 지우면 조용히 협상이 바뀐다
        assertThat(request.getHeader("Accept")).isEqualTo("application/json, */*");
    }

    private SaraminApiSpecFile specWithRegionMapping(Map<String, String> saraminToOurs) {
        return new SaraminApiSpecFile(null, null,
                new SaraminApiSpecFile.Mapping(false, saraminToOurs, false, Map.of(), Set.of()));
    }

    private SaraminJobVacancyAdapter adapter(String accessKey, SaraminApiSpecFile spec) {
        SaraminApiSpecLoader specLoader = new FixedSpecLoader(spec);
        SaraminJobSearchClient client = new SaraminJobSearchClient(
                RestClient.create(),
                new ExternalApiMetrics(new SimpleMeterRegistry()),
                server.url("/").toString().replaceAll("/$", ""),
                "/job-search",
                accessKey);
        return new SaraminJobVacancyAdapter(
                client,
                new SaraminJobVacancyParser(new SaraminResponseReader(new ObjectMapper())),
                specLoader,
                new SaraminLocCodeResolver(specLoader));
    }

    /** 파일을 읽지 않고 주어진 스펙만 돌려주는 로더. */
    private static final class FixedSpecLoader extends SaraminApiSpecLoader {
        private final SaraminApiSpecFile fixed;

        private FixedSpecLoader(SaraminApiSpecFile fixed) {
            super(new ObjectMapper(), new DefaultResourceLoader(), "classpath:saramin/absent.json");
            this.fixed = fixed;
        }

        @Override
        public SaraminApiSpecFile spec() {
            return fixed;
        }
    }
}
