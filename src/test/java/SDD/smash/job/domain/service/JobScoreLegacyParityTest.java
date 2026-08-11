package SDD.smash.job.domain.service;

import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.job.domain.model.RegionJobCount;
import SDD.smash.legacy.job.Dto.JobCountDTO;
import SDD.smash.legacy.job.Repository.JobCodeMiddleRepository;
import SDD.smash.legacy.job.Repository.JobCountRepository;
import SDD.smash.legacy.job.Service.JobScoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 이관 안전망. 옛 {@code JobScoreService} 의 점수 공식과 새 {@code JobScorePolicy} 가
 * <b>같은 입력에 같은 값</b>을 내는지 대조한다.
 *
 * <p>추천 API 는 {@code recommendation} 이관(6단계) 전까지 옛 경로를 계속 쓴다.
 * 두 구현이 공존하는 동안 결과가 갈라지면 이 테스트가 먼저 깨진다.
 * 옛 클래스가 삭제되는 6단계에서 이 테스트도 함께 사라진다.
 *
 * <p>옛 서비스는 공식이 메서드 안에 인라인돼 있어 순수 호출이 불가능하다.
 * 그래서 협력자를 대역으로 세우고 캐시는 항상 미스가 되게 해 계산 경로만 통과시킨다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobScoreLegacyParityTest {

    @Mock
    private JobCountRepository legacyJobCountRepository;
    @Mock
    private JobCodeMiddleRepository legacyJobCodeMiddleRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private final JobScorePolicy policy = new JobScorePolicy();

    private JobScoreService legacyService() {
        // 캐시는 항상 미스 → 옛 서비스가 계산 경로를 타게 한다
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of());
        return new JobScoreService(legacyJobCountRepository, redisTemplate, legacyJobCodeMiddleRepository);
    }

    /** 같은 원천 데이터를 옛 DTO 와 새 도메인 모델로 각각 만든다. */
    private static List<JobCountDTO> toLegacy(List<long[]> rows) {
        List<JobCountDTO> list = new ArrayList<>();
        for (long[] row : rows) {
            list.add(new JobCountDTO(codeOf(row[0]), row[1] < 0 ? null : row[1]));
        }
        return list;
    }

    private static List<RegionJobCount> toDomain(List<long[]> rows) {
        List<RegionJobCount> list = new ArrayList<>();
        for (long[] row : rows) {
            // 어댑터가 null 합계를 0 으로 보정하므로 도메인 쪽 입력도 0 이다
            list.add(new RegionJobCount(SigunguCode.of(codeOf(row[0])), row[1] < 0 ? 0L : row[1]));
        }
        return list;
    }

    private static String codeOf(long index) {
        return String.format("%05d", 11000 + index);
    }

    private static Map<String, Integer> flatten(Map<SigunguCode, Score> scores) {
        Map<String, Integer> flat = new LinkedHashMap<>();
        scores.forEach((code, score) -> flat.put(code.value(), score.value()));
        return flat;
    }

    /** count 가 -1 이면 "합계 null" 을 뜻한다. */
    private static final List<List<long[]>> SAMPLES = List.of(
            List.of(),
            List.of(new long[]{1, 0}),
            List.of(new long[]{1, 0}, new long[]{2, 0}, new long[]{3, 0}),
            List.of(new long[]{1, 100}),
            List.of(new long[]{1, 100}, new long[]{2, 50}, new long[]{3, 0}),
            List.of(new long[]{1, 3}, new long[]{2, 7}, new long[]{3, 10}),
            List.of(new long[]{1, 1}, new long[]{2, 3}),
            List.of(new long[]{1, 999_999}, new long[]{2, 1}),
            List.of(new long[]{1, 7}, new long[]{2, 7}, new long[]{3, 7}),
            List.of(new long[]{1, -1}, new long[]{2, 5}),
            List.of(new long[]{1, -1}, new long[]{2, -1}),
            List.of(new long[]{1, 2}, new long[]{2, 3}, new long[]{3, 5}, new long[]{4, 8}, new long[]{5, 13}));

    @Test
    @DisplayName("전체 일자리 기준 점수가 옛 구현과 모든 표본에서 같다")
    void totalJobScoreMatchesLegacy() {
        for (List<long[]> sample : SAMPLES) {
            // given
            JobScoreService legacy = legacyService();
            given(legacyJobCountRepository.findAllTotalJobCount()).willReturn(toLegacy(sample));

            // when
            Map<String, Integer> legacyScores = legacy.getJobScore(null);
            Map<String, Integer> newScores = flatten(policy.scores(toDomain(sample)));

            // then
            assertThat(newScores)
                    .as("sample=%s", Arrays.deepToString(sample.toArray()))
                    .isEqualTo(legacyScores);
        }
    }

    @Test
    @DisplayName("특정 직종 기준 점수가 옛 구현과 모든 표본에서 같다")
    void jobCodeScoreMatchesLegacy() {
        for (List<long[]> sample : SAMPLES) {
            // given
            JobScoreService legacy = legacyService();
            given(legacyJobCodeMiddleRepository.existsByCode(anyString())).willReturn(true);
            given(legacyJobCountRepository.findAllJobCode(anyString())).willReturn(toLegacy(sample));

            // when
            Map<String, Integer> legacyScores = legacy.getJobScore("011");
            Map<String, Integer> newScores = flatten(policy.scores(toDomain(sample)));

            // then
            assertThat(newScores)
                    .as("sample=%s", Arrays.deepToString(sample.toArray()))
                    .isEqualTo(legacyScores);
        }
    }

    @Test
    @DisplayName("옛 구현과 새 정책 모두 지역 순서를 입력 순서 그대로 유지한다")
    void preservesInputOrder() {
        // given
        List<long[]> sample = List.of(new long[]{5, 10}, new long[]{1, 20}, new long[]{3, 30});
        JobScoreService legacy = legacyService();
        given(legacyJobCountRepository.findAllTotalJobCount()).willReturn(toLegacy(sample));

        // when
        Map<String, Integer> legacyScores = legacy.getJobScore(null);
        Map<String, Integer> newScores = flatten(policy.scores(toDomain(sample)));

        // then
        assertThat(newScores.keySet()).containsExactlyElementsOf(legacyScores.keySet());
    }
}
