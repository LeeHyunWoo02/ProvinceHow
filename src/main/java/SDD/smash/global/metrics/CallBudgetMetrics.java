package SDD.smash.global.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 외부 API 일일 호출 예산의 소모량을 노출하는 계측기.
 *
 * <p>일부 수집원은 일일 호출 한도가 있고, 그 한도는 <b>배치와 사용자 조회가 공유</b>한다
 * (사람인 500회/일). 배치가 예산을 다 쓰면 사용자 조회가 막히므로, 남은 예산은
 * 사후에 로그로 확인할 값이 아니라 <b>실시간으로 보여야 하는 값</b>이다.
 *
 * <p>메트릭
 * <ul>
 *   <li>{@code smash_external_api_budget_used{api}} - 오늘 쓴 호출 수</li>
 *   <li>{@code smash_external_api_budget_limit{api}} - 일일 상한</li>
 * </ul>
 * 소모율은 Prometheus 에서 {@code used / limit} 으로 계산한다(상한이 설정값이라 거의 상수다).
 *
 * <p>게이지는 값을 저장하지 않고 <b>조회 시점에 공급자를 호출</b>한다. 그래서 예산을 세는
 * 주체(어댑터)가 자기 상태를 그대로 노출하면 되고, 이 클래스가 어댑터를 알 필요가 없다 -
 * {@code global} 이 특정 컨텍스트의 {@code infrastructure} 를 참조하지 않기 위한 방향이다.
 */
@Component
@RequiredArgsConstructor
public class CallBudgetMetrics {

    private static final String USED = "smash.external.api.budget.used";
    private static final String LIMIT = "smash.external.api.budget.limit";

    private final MeterRegistry registry;

    /**
     * 한 수집원의 예산 게이지를 등록한다. 같은 {@code api} 로 두 번 등록하면 먼저 등록한 것이 남는다.
     *
     * @param api   수집원 이름. {@link ExternalApiMetrics} 의 {@code api} 태그와 같은 값을 쓴다
     * @param used  오늘 쓴 호출 수 공급자. 게이지가 스크랩될 때마다 호출된다
     * @param limit 일일 상한 공급자
     */
    public void register(String api, Supplier<Number> used, Supplier<Number> limit) {
        Gauge.builder(USED, used)
                .description("오늘 사용한 외부 API 호출 수")
                .tag("api", api)
                .strongReference(true)
                .register(registry);

        Gauge.builder(LIMIT, limit)
                .description("외부 API 일일 호출 상한")
                .tag("api", api)
                .strongReference(true)
                .register(registry);
    }
}
