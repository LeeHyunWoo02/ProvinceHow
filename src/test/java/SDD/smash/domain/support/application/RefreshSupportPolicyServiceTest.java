package SDD.smash.domain.support.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.support.domain.model.SupportPolicy;
import SDD.smash.domain.support.domain.model.SupportPolicyCollection;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.domain.support.domain.port.SupportPolicyProvider;
import SDD.smash.domain.support.domain.port.SupportPolicyRepository;
import SDD.smash.domain.support.domain.port.SupportScoreCache;
import SDD.smash.global.domain.model.SigunguCode;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * 리프레시 유스케이스. 포트를 목킹/Fake 로 대체하고 <b>수집 실패가 기존 정책을 지우지 않는지</b>와
 * <b>두 종료 조건(연속 실패·데드라인)</b>을 본다.
 */
@ExtendWith(MockitoExtension.class)
class RefreshSupportPolicyServiceTest {

    private static final SigunguCode JONGNO = SigunguCode.of("11110");
    private static final SigunguCode JUNGGU = SigunguCode.of("11140");
    private static final SupportPolicy EXISTING =
            new SupportPolicy("작년부터 있던 정책", "https://example.test/old", "주거지원");
    private static final SupportPolicy FRESH =
            new SupportPolicy("새로 받은 정책", "https://example.test/new", "주거지원");

    /** 데드라인을 검증하지 않는 테스트는 시간이 흐르지 않는 시계를 쓴다. */
    private static final Clock FROZEN = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @Mock
    private AddressQueryService addressQueryService;
    @Mock
    private SupportPolicyProvider supportPolicyProvider;
    @Mock
    private SupportScoreCache supportScoreCache;

    private InMemorySupportPolicyRepository supportPolicyRepository;

    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        supportPolicyRepository = new InMemorySupportPolicyRepository();

        logs = new ListAppender<>();
        logs.start();
        serviceLogger = (Logger) LoggerFactory.getLogger(RefreshSupportPolicyService.class);
        serviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logs);
    }

    /** 종료 조건 없이(=끝까지 순회) 도는 유스케이스. */
    private RefreshSupportPolicyService service() {
        return service(0, 0, FROZEN);
    }

    private RefreshSupportPolicyService service(int maxConsecutiveFailures, long deadlineMinutes, Clock clock) {
        return new RefreshSupportPolicyService(addressQueryService, supportPolicyProvider,
                supportPolicyRepository, supportScoreCache, maxConsecutiveFailures, deadlineMinutes, clock);
    }

    // ------------------------------------------------------------------ 보존 / 집계

    @Test
    @DisplayName("수집에 실패한 조합은 저장을 건너뛰어 기존 정책이 그대로 남는다")
    void skipsSavingAndPreservesExistingPoliciesWhenCollectionFails() {
        // given — 이미 저장돼 있던 정책과, 전 조합 수집 실패
        given(addressQueryService.getAllSigunguCodes()).willReturn(List.of(JONGNO));
        supportPolicyRepository.saveAll(JONGNO, SupportTag.HOUSING_SUPPORT, List.of(EXISTING));
        supportPolicyRepository.resetCallCount();
        given(supportPolicyProvider.fetch(any(), any())).willReturn(SupportPolicyCollection.notCollected());

        // when
        service().refreshAll();

        // then — saveAll 이 아예 불리지 않아야 기존 값이 빈 payload 로 덮이지 않는다
        assertThat(supportPolicyRepository.saveCalls()).isZero();
        assertThat(supportPolicyRepository.findBy(JONGNO, SupportTag.HOUSING_SUPPORT)).containsExactly(EXISTING);
        then(supportScoreCache).should().evictAll();
    }

    @Test
    @DisplayName("정말 0건인 응답은 0건으로 저장해 기존 정책을 비운다")
    void savesEmptyResultWhenApiTrulyReturnsNoPolicy() {
        // given
        given(addressQueryService.getAllSigunguCodes()).willReturn(List.of(JONGNO));
        supportPolicyRepository.saveAll(JONGNO, SupportTag.HOUSING_SUPPORT, List.of(EXISTING));
        supportPolicyRepository.resetCallCount();
        given(supportPolicyProvider.fetch(any(), any())).willReturn(SupportPolicyCollection.of(List.of()));

        // when
        service().refreshAll();

        // then — 수집 실패와 달리 저장이 일어난다(4개 태그 전부)
        assertThat(supportPolicyRepository.saveCalls()).isEqualTo(SupportTag.values().length);
        assertThat(supportPolicyRepository.findBy(JONGNO, SupportTag.HOUSING_SUPPORT)).isEmpty();
    }

    @Test
    @DisplayName("집계는 반복 횟수가 아니라 실제 적재된 정책 수와 성공·건너뜀·실패 조합 수를 센다")
    void countsActualPolicyCountAndCombinationOutcomes() {
        // given — 성공 2건 / 0건 성공 / 수집 실패 / 저장 실패 를 한 시군구 안에 만든다
        given(addressQueryService.getAllSigunguCodes()).willReturn(List.of(JONGNO));
        given(supportPolicyProvider.fetch(eq(JONGNO), eq(SupportTag.HOUSING_SUPPORT)))
                .willReturn(SupportPolicyCollection.of(List.of(FRESH, FRESH, FRESH)));
        given(supportPolicyProvider.fetch(eq(JONGNO), eq(SupportTag.LONG_TERM_UNEMPLOYED_YOUTH)))
                .willReturn(SupportPolicyCollection.notCollected());
        given(supportPolicyProvider.fetch(eq(JONGNO), eq(SupportTag.INTERN)))
                .willReturn(SupportPolicyCollection.of(List.of()));
        given(supportPolicyProvider.fetch(eq(JONGNO), eq(SupportTag.LOAN)))
                .willReturn(SupportPolicyCollection.of(List.of(FRESH)));
        supportPolicyRepository.failOn(SupportTag.LOAN);

        // when
        service().refreshAll();

        // then
        assertThat(completionLog())
                .contains("saved=3")
                .contains("combinations=4")
                .contains("succeeded=2")
                .contains("skipped=1")
                .contains("failed=1")
                .contains("stopped=NONE")
                .contains("remaining=0");
    }

    // ------------------------------------------------------------------ 연속 실패 중단

    @Test
    @DisplayName("연속 실패가 임계에 도달하면 중단하고 그 전에 성공한 조합은 저장돼 있다")
    void stopsWhenConsecutiveFailuresReachThreshold() {
        // given — 첫 조합만 성공하고 이후 전부 수집 실패. 임계 3.
        given(addressQueryService.getAllSigunguCodes()).willReturn(List.of(JONGNO, JUNGGU));
        given(supportPolicyProvider.fetch(any(), any())).willAnswer(invocation -> {
            SigunguCode code = invocation.getArgument(0);
            SupportTag tag = invocation.getArgument(1);
            return (code.equals(JONGNO) && tag == SupportTag.HOUSING_SUPPORT)
                    ? SupportPolicyCollection.of(List.of(FRESH))
                    : SupportPolicyCollection.notCollected();
        });

        // when
        service(3, 0, FROZEN).refreshAll();

        // then — 8개 조합 중 4개만 돌고 멈춘다. 이미 저장한 조합은 남는다.
        assertThat(supportPolicyRepository.findBy(JONGNO, SupportTag.HOUSING_SUPPORT)).containsExactly(FRESH);
        assertThat(completionLog())
                .contains("combinations=4")
                .contains("succeeded=1")
                .contains("skipped=3")
                .contains("stopped=CONSECUTIVE_FAILURES")
                .contains("remaining=4");
        assertThat(stopLog()).contains("CONSECUTIVE_FAILURES").contains("남은 조합=4");
        then(supportScoreCache).should().evictAll();
    }

    @Test
    @DisplayName("중간에 성공하면 연속 실패 카운터가 초기화되어 중단되지 않는다")
    void resetsConsecutiveFailureCounterOnSuccess() {
        // given — 실패와 성공이 번갈아 나오면 연속 실패는 1을 넘지 않는다. 임계 2.
        given(addressQueryService.getAllSigunguCodes()).willReturn(List.of(JONGNO, JUNGGU));
        given(supportPolicyProvider.fetch(any(), any())).willAnswer(invocation -> {
            SupportTag tag = invocation.getArgument(1);
            boolean succeeds = tag == SupportTag.HOUSING_SUPPORT || tag == SupportTag.INTERN;
            return succeeds ? SupportPolicyCollection.of(List.of(FRESH)) : SupportPolicyCollection.notCollected();
        });

        // when
        service(2, 0, FROZEN).refreshAll();

        // then — 8개 조합을 전부 돈다
        assertThat(completionLog())
                .contains("combinations=8")
                .contains("succeeded=4")
                .contains("skipped=4")
                .contains("stopped=NONE")
                .contains("remaining=0");
    }

    // ------------------------------------------------------------------ 데드라인

    @Test
    @DisplayName("데드라인을 넘기면 남은 조합을 건너뛰고 정상 종료한다")
    void stopsWhenDeadlineExceeded() {
        // given — 시계를 한 번 읽을 때마다 30초씩 흐른다. 데드라인 1분.
        given(addressQueryService.getAllSigunguCodes()).willReturn(List.of(JONGNO));
        given(supportPolicyProvider.fetch(any(), any())).willReturn(SupportPolicyCollection.of(List.of(FRESH)));

        // when
        service(0, 1, steppingClock(30_000L)).refreshAll();

        // then — 첫 조합만 처리하고 나머지는 건너뛴다(예외 없이 끝난다)
        assertThat(supportPolicyRepository.saveCalls()).isEqualTo(1);
        assertThat(completionLog())
                .contains("combinations=1")
                .contains("succeeded=1")
                .contains("stopped=DEADLINE")
                .contains("remaining=3");
        assertThat(stopLog()).contains("DEADLINE").contains("남은 조합=3");
        then(supportScoreCache).should().evictAll();
    }

    // ------------------------------------------------------------------ 보조

    private String completionLog() {
        return logLine(Level.INFO, "[SupportRefresh] 완료");
    }

    private String stopLog() {
        return logLine(Level.WARN, "[SupportRefresh] 중단");
    }

    private String logLine(Level level, String prefix) {
        return logs.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError(prefix + " 로그가 없다"));
    }

    /** 읽을 때마다 일정 시간이 흐르는 시계. 실제로 기다리지 않고 데드라인을 재현한다. */
    private static Clock steppingClock(long stepMillis) {
        return new Clock() {
            private long millis;

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                millis += stepMillis;
                return Instant.ofEpochMilli(millis);
            }
        };
    }

    /** 정본 저장소 Fake. "기존 데이터가 남아 있는가"를 그대로 확인하려고 목킹 대신 인메모리로 둔다. */
    private static class InMemorySupportPolicyRepository implements SupportPolicyRepository {

        private final Map<String, List<SupportPolicy>> store = new HashMap<>();
        private final Set<SupportTag> failing = EnumSet.noneOf(SupportTag.class);
        private int saveCalls;

        @Override
        public List<SupportPolicy> findBy(SigunguCode code, SupportTag tag) {
            return store.getOrDefault(key(code, tag), List.of());
        }

        @Override
        public int countBy(SigunguCode code, SupportTag tag) {
            return findBy(code, tag).size();
        }

        @Override
        public void saveAll(SigunguCode code, SupportTag tag, List<SupportPolicy> policies) {
            if (failing.contains(tag)) {
                throw new IllegalStateException("저장소 장애");
            }
            saveCalls++;
            store.put(key(code, tag), List.copyOf(policies));
        }

        void failOn(SupportTag tag) {
            failing.add(tag);
        }

        int saveCalls() {
            return saveCalls;
        }

        void resetCallCount() {
            saveCalls = 0;
        }

        private String key(SigunguCode code, SupportTag tag) {
            return code.value() + ":" + tag.name();
        }
    }
}
