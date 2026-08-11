package SDD.smash.infra.domain.service;

import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.infra.domain.model.Major;
import SDD.smash.infra.domain.model.RegionMajorScore;
import SDD.smash.legacy.infra.Dto.SigunguMajorAvgDTO;
import SDD.smash.legacy.infra.Repository.InfraRepository;
import SDD.smash.legacy.infra.Service.InfraScoreService;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 이관 안전망. 옛 {@code InfraScoreService.getInfraScoresByChoice} 와
 * 새 {@code InfraScorePolicy} 가 <b>같은 원천 데이터에 같은 값</b>을 내는지 대조한다.
 *
 * <p>추천 API 는 {@code recommendation} 이관(6단계) 전까지 옛 경로를 계속 쓴다.
 * 두 구현이 공존하는 동안 결과가 갈라지면 이 테스트가 먼저 깨진다.
 * 옛 클래스가 삭제되는 6단계에서 이 테스트도 함께 사라진다.
 *
 * <p>옛/새 {@code Major} 는 별개 타입이지만 선언 순서(HEALTH, FOOD, CULTURE, LIFE)가
 * 같아 {@code ordinal()} 로 1:1 대응시킬 수 있다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InfraScoreLegacyParityTest {

    @Mock
    private InfraRepository legacyInfraRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private final InfraScorePolicy policy = new InfraScorePolicy();

    private InfraScoreService legacyService() {
        given(redisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(anyString())).willReturn(Map.of());
        return new InfraScoreService(legacyInfraRepository, redisTemplate);
    }

    /** 각 표본 행은 (시군구 인덱스, 대분류 서수 0~3, 점수-또는-null)이다. */
    private static final List<Object[]> SAMPLE_ROWS = List.of(
            new Object[]{1, 0, 80.0},   // HEALTH
            new Object[]{1, 1, 60.0},   // FOOD
            new Object[]{1, 2, 40.0},   // CULTURE
            new Object[]{1, 3, 20.0},   // LIFE
            new Object[]{2, 0, 100.0},
            new Object[]{2, 1, null},   // AVG(score) 가 null 인 행
            new Object[]{3, 2, 55.5},
            new Object[]{3, 3, 55.5});

    private static String sigunguCodeOf(int index) {
        return String.format("%05d", 11000 + index);
    }

    private static SDD.smash.legacy.infra.Entity.Major legacyMajorOf(int ordinal) {
        return SDD.smash.legacy.infra.Entity.Major.values()[ordinal];
    }

    private static Major newMajorOf(int ordinal) {
        return Major.values()[ordinal];
    }

    private static Map<String, Integer> flatten(Map<SigunguCode, Score> scores) {
        Map<String, Integer> flat = new LinkedHashMap<>();
        scores.forEach((code, score) -> flat.put(code.value(), score.value()));
        return flat;
    }

    @Test
    @DisplayName("모든 infraChoice(0~15)에 대해 점수가 옛 구현과 같다")
    void scoresMatchLegacyForEveryChoice() {
        for (int choice = 0; choice <= 15; choice++) {

            // given: 이 choice 가 고르는 대분류들만 걸러 원천 데이터를 만든다
            EnumSet<SDD.smash.legacy.infra.Entity.Major> legacySelected =
                    SDD.smash.legacy.infra.Entity.Major.fromChoiceMask(choice);
            EnumSet<Major> newSelected = Major.fromChoiceMask(choice);

            List<SigunguMajorAvgDTO> legacyRows = new ArrayList<>();
            List<RegionMajorScore> newRows = new ArrayList<>();
            for (Object[] sample : SAMPLE_ROWS) {
                int sigunguIndex = (int) sample[0];
                int majorOrdinal = (int) sample[1];
                Double score = (Double) sample[2];
                if (!legacySelected.contains(legacyMajorOf(majorOrdinal))) {
                    continue;
                }
                String sigunguCode = sigunguCodeOf(sigunguIndex);
                legacyRows.add(new SigunguMajorAvgDTO(sigunguCode, legacyMajorOf(majorOrdinal), score));
                newRows.add(new RegionMajorScore(SigunguCode.of(sigunguCode), newMajorOf(majorOrdinal), score));
            }

            InfraScoreService legacy = legacyService();
            given(legacyInfraRepository.sumScoreBySigunguAndMajor(legacySelected)).willReturn(legacyRows);

            // when
            Map<String, Integer> legacyScores = legacy.getInfraScoresByChoice(choice);
            Map<String, Integer> newScores = flatten(policy.scores(newSelected, newRows));

            // then
            assertThat(newScores)
                    .as("infraChoice=%d", choice)
                    .isEqualTo(legacyScores);
        }
    }

    @Test
    @DisplayName("infraChoice 가 null 이면 둘 다 빈 맵이다")
    void bothReturnEmptyMapWhenChoiceIsNull() {
        InfraScoreService legacy = legacyService();

        Map<String, Integer> legacyScores = legacy.getInfraScoresByChoice(null);
        Map<String, Integer> newScores = flatten(policy.scores(Major.fromChoiceMask(0), List.of()));

        assertThat(legacyScores).isEmpty();
        assertThat(newScores).isEmpty();
    }
}
