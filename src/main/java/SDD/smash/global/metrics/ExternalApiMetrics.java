package SDD.smash.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 외부 API 호출 결과를 세는 계측기.
 *
 * <p>이 시스템의 데이터는 대부분 외부 공공 API 수집에 의존하고, 수집 실패는 조용하다 -
 * 실패한 조합은 저장을 건너뛰어 <b>기존 데이터를 보존</b>하는 정책이라 앱은 정상으로 보인다.
 * 그래서 "얼마나 실패하고 있는가"는 로그를 뒤지지 않으면 알 수 없었다. 이 계측기가 그 값이다.
 *
 * <p>메트릭: {@code smash_external_api_calls_total{api, outcome}} (outcome = success|failure)
 *
 * <p>태그 키는 항상 {@code api}, {@code outcome} 두 개로 고정한다. 실패 사유별 태그를 더하면
 * 같은 메트릭 이름에 태그 키가 달라져 Prometheus 노출 시 Micrometer 가 거부한다.
 * 사유는 기존 {@code log.warn} 이 남긴다.
 *
 * <p>{@code api} 값은 수집원 단위다: {@code youthcenter}, {@code localdata}, {@code kosis},
 * {@code molit}, {@code saramin}. 하나의 수집원에 어댑터가 여러 개 있어도 같은 값을 쓴다
 * (일일 호출 한도가 수집원 단위로 걸리기 때문이다).
 */
@Component
@RequiredArgsConstructor
public class ExternalApiMetrics {

    private static final String CALLS = "smash.external.api.calls";
    private static final String DESCRIPTION = "외부 API 호출 횟수. outcome=success|failure";

    private final MeterRegistry registry;

    public void success(String api) {
        count(api, "success");
    }

    public void failure(String api) {
        count(api, "failure");
    }

    /**
     * 설정이 없어(예: API 키 미설정) <b>호출 자체를 하지 않은</b> 경우.
     *
     * <p>실패와 구분한다. 키가 비어 있으면 수집이 전량 실패하는데, 그것을 {@code failure} 로 세면
     * "서버가 죽었다"와 "우리가 키를 안 넣었다"가 같은 그래프로 보인다.
     */
    public void skipped(String api) {
        count(api, "skipped");
    }

    private void count(String api, String outcome) {
        Counter.builder(CALLS)
                .description(DESCRIPTION)
                .tag("api", api)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }
}
