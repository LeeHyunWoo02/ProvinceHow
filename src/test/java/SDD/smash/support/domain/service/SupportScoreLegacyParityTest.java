package SDD.smash.support.domain.service;

import SDD.smash.common.domain.model.Score;
import SDD.smash.support.domain.model.SupportTag;
import SDD.smash.legacy.address.Dto.SigunguCodeDTO;
import SDD.smash.legacy.address.Repository.SigunguRepository;
import SDD.smash.legacy.support.service.SupportScoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 이관 안전망. 옛 {@code SupportScoreService.getSupportScoresByTag} 의 점수 공식과
 * 새 {@code SupportScorePolicy} 가 <b>같은 원천 데이터에 같은 값</b>을 내는지 대조한다.
 *
 * <p>추천 API 는 {@code recommendation} 이관(7단계) 전까지 옛 경로를 계속 쓴다.
 * 두 구현이 공존하는 동안 결과가 갈라지면 이 테스트가 먼저 깨진다.
 * 옛 클래스가 삭제되는 단계에서 이 테스트도 함께 사라진다.
 *
 * <p>옛 서비스는 시군구 하나("11000")만 다루도록 목킹해 결과 맵에서 그 값만 꺼내 비교한다.
 * 옛/새 {@code SupportTag} 는 별개 타입이지만 선언 순서가 같아 {@code ordinal()} 로 대응시킨다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupportScoreLegacyParityTest {

    private static final String SIGUNGU_CODE = "11000";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private SigunguRepository legacySigunguRepository;

    private final SupportScorePolicy policy = new SupportScorePolicy();

    private SupportScoreService legacyService() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of());
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(legacySigunguRepository.findAllSigunguCodes())
                .willReturn(List.of(new SigunguCodeDTO(SIGUNGU_CODE)));
        return new SupportScoreService(redisTemplate, legacySigunguRepository);
    }

    private static SDD.smash.legacy.support.domain.SupportTag legacyTagOf(int ordinal) {
        return SDD.smash.legacy.support.domain.SupportTag.values()[ordinal];
    }

    private static SupportTag newTagOf(int ordinal) {
        return SupportTag.values()[ordinal];
    }

    /** 각 표본은 (태그 서수 0~3, 개수-또는-null) 이다. */
    private static final List<Object[]> SAMPLE_COUNTS = List.of(
            new Object[]{0, 5},     // HOUSING_SUPPORT: 있음
            new Object[]{1, 0},     // LONG_TERM_UNEMPLOYED_YOUTH: 0건
            new Object[]{2, null},  // INTERN: 데이터 없음
            new Object[]{3, 12});   // LOAN: 있음

    @Test
    @DisplayName("모든 supportChoice(0~15)에 대해 점수가 옛 구현과 같다")
    void scoresMatchLegacyForEveryChoice() {
        for (int choice = 0; choice <= 15; choice++) {

            EnumSet<SDD.smash.legacy.support.domain.SupportTag> legacySelected =
                    SDD.smash.legacy.support.domain.SupportTag.fromChoiceMask(choice);
            EnumSet<SupportTag> newSelected = SupportTag.fromChoiceMask(choice);
            if (newSelected.isEmpty()) {
                continue; // 선택 없음은 두 구현 모두 별도 분기(빈 맵)로 처리 — 공식 대조 대상이 아니다
            }

            SupportScoreService legacy = legacyService();

            Map<SupportTag, Integer> countsByTagNew = new LinkedHashMap<>();
            for (Object[] sample : SAMPLE_COUNTS) {
                int ordinal = (int) sample[0];
                Integer count = (Integer) sample[1];

                SDD.smash.legacy.support.domain.SupportTag legacyTag = legacyTagOf(ordinal);
                if (legacySelected.contains(legacyTag)) {
                    String key = SIGUNGU_CODE + ":" + legacyTag.getValue() + ":NUM";
                    given(valueOperations.get(key)).willReturn(count);
                }
                countsByTagNew.put(newTagOf(ordinal), count);
            }

            // when
            Map<String, Integer> legacyScores = legacy.getSupportScoresByTag(choice);
            Score newScore = policy.score(countsByTagNew, newSelected);

            // then
            assertThat(newScore.value())
                    .as("supportChoice=%d", choice)
                    .isEqualTo(legacyScores.get(SIGUNGU_CODE));
        }
    }
}
