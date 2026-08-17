package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.ExperienceLevel;
import SDD.smash.domain.job.domain.model.JobPostingSample;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.global.domain.model.SigunguCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SaraminRegionProfileAdapterTest {

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
    @DisplayName("access-key 가 비어 있으면 호출하지 않고 빈 표본")
    void emptyWithoutAccessKey() {
        SaraminRegionProfileAdapter adapter = adapter("", specWithRegionMapping(Map.of("101000", "11680")));

        assertThat(adapter.sample(SigunguCode.of("11680"), 100)).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("지역 역매핑이 비어 있으면(현재 상태) 호출하지 않고 빈 표본")
    void emptyWhenRegionNotReverseMapped() {
        SaraminRegionProfileAdapter adapter = adapter(ACCESS_KEY, SaraminApiSpecFile.defaults());

        assertThat(adapter.sample(SigunguCode.of("11680"), 100)).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("역매핑이 있으면 loc_cd 로 조회하고 연봉·경력·업종을 표본으로 파싱한다")
    void mapsSamplesWhenReverseMappingPresent() throws InterruptedException {
        // given
        SaraminRegionProfileAdapter adapter =
                adapter(ACCESS_KEY, specWithRegionMapping(Map.of("101000", "11680")));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json;charset=UTF-8")
                .setBody("""
                        {
                          "jobs": { "count": 2, "start": 0, "total": "2", "job": [
                            { "salary": { "name": "3,000~5,000만원" },
                              "position": { "experience-level": { "code": "1" },
                                            "industry": { "name": "IT·웹·통신" } } },
                            { "salary": { "name": "회사내규에 따름" },
                              "position": { "experience-level": { "code": "2" },
                                            "industry": { "name": "금융·보험" } } }
                          ] }
                        }
                        """));

        // when
        List<JobPostingSample> samples = adapter.sample(SigunguCode.of("11680"), 100);

        // then
        assertThat(samples).hasSize(2);
        // 첫 표본: 3000~5000만원 파싱, 신입
        assertThat(samples.get(0).salaryMidpointManwon().getAsInt()).isEqualTo(4000);
        assertThat(samples.get(0).experienceLevel()).isEqualTo(ExperienceLevel.NEWCOMER);
        assertThat(samples.get(0).industryName()).isEqualTo("IT·웹·통신");
        // 둘째 표본: "회사내규" → 연봉 파싱 불가(비어 있음), 경력
        assertThat(samples.get(1).salaryMidpointManwon().isEmpty()).isTrue();
        assertThat(samples.get(1).experienceLevel()).isEqualTo(ExperienceLevel.EXPERIENCED);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath())
                .contains("/job-search")
                .contains("access-key=" + ACCESS_KEY)
                .contains("loc_cd=101000")
                .contains("count=100");
    }

    @Test
    @DisplayName("연봉 파싱 - 월급 제외, 억 환산, 만원 구간, 회사내규 제외")
    void parsesSalaryWithUnitRules() {
        // given
        SaraminRegionProfileAdapter adapter =
                adapter(ACCESS_KEY, specWithRegionMapping(Map.of("101000", "11680")));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json;charset=UTF-8")
                .setBody("""
                        {
                          "jobs": { "count": 4, "start": 0, "total": "4", "job": [
                            { "salary": { "name": "월 250만원" }, "position": {} },
                            { "salary": { "name": "1억 2,000만원" }, "position": {} },
                            { "salary": { "name": "3,000~4,000만원" }, "position": {} },
                            { "salary": { "name": "회사내규에 따름" }, "position": {} }
                          ] }
                        }
                        """));

        // when
        List<JobPostingSample> samples = adapter.sample(SigunguCode.of("11680"), 100);

        // then
        assertThat(samples).hasSize(4);
        assertThat(samples.get(0).salaryMidpointManwon().isEmpty()).isTrue();      // 월급 → 제외
        assertThat(samples.get(1).salaryMidpointManwon().getAsInt()).isEqualTo(12000); // 1억2천 → 12000
        assertThat(samples.get(2).salaryMidpointManwon().getAsInt()).isEqualTo(3500);  // 3000~4000 → 3500
        assertThat(samples.get(3).salaryMidpointManwon().isEmpty()).isTrue();      // 회사내규 → 제외
    }

    private SaraminApiSpecFile specWithRegionMapping(Map<String, String> saraminToOurs) {
        return new SaraminApiSpecFile(null, null,
                new SaraminApiSpecFile.Mapping(false, saraminToOurs, false, Map.of(), Set.of()));
    }

    private SaraminRegionProfileAdapter adapter(String accessKey, SaraminApiSpecFile spec) {
        SaraminApiSpecLoader specLoader = new FixedSpecLoader(spec);
        return new SaraminRegionProfileAdapter(
                new RestTemplate(),
                new SaraminJobSampleParser(new ObjectMapper()),
                specLoader,
                new SaraminLocCodeResolver(specLoader),
                server.url("/").toString().replaceAll("/$", ""),
                "/job-search",
                accessKey);
    }

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
