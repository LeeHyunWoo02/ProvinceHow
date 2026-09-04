package SDD.smash.domain.support.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.support.domain.model.SupportScoreKey;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.domain.support.domain.port.SupportPolicyRepository;
import SDD.smash.domain.support.domain.port.SupportScoreCache;
import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

/**
 * 지원정책 점수 유스케이스. 포트를 목킹해 캐시 hit/miss 경로와 <b>새 다건 조회 경로</b>
 * (선택 태그별 {@code countByTagForAll} 1회씩)를 본다(backend-conventions §7.4, redis-conventions §7.1).
 * {@code SupportScorePolicy} 는 서비스가 직접 {@code new} 로 쓰는 순수 함수라 실제로 계산된다.
 */
@ExtendWith(MockitoExtension.class)
class SupportScoreServiceTest {

    private static final SigunguCode JONGNO = SigunguCode.of("11110");
    private static final SigunguCode JUNGGU = SigunguCode.of("11140");

    /** HOUSING_SUPPORT(1<<3=8) | LOAN(1) = 9 → 태그 2개 선택. */
    private static final int HOUSING_AND_LOAN = 9;

    @Mock
    private AddressQueryService addressQueryService;
    @Mock
    private SupportPolicyRepository supportPolicyRepository;
    @Mock
    private SupportScoreCache supportScoreCache;

    private SupportScoreService service;

    @BeforeEach
    void setUp() {
        service = new SupportScoreService(addressQueryService, supportPolicyRepository, supportScoreCache);
    }

    @Test
    @DisplayName("캐시가 있으면 저장소와 주소 서비스를 조회하지 않는다")
    void returnsCachedScoresWithoutQueryingRepository() {
        // given
        Map<SigunguCode, Score> cached = Map.of(JONGNO, Score.of(100));
        given(supportScoreCache.find(eq(SupportScoreKey.of(HOUSING_AND_LOAN)))).willReturn(Optional.of(cached));

        // when
        Map<SigunguCode, Score> result = service.scoresFor(HOUSING_AND_LOAN);

        // then
        assertThat(result).isEqualTo(cached);
        then(supportPolicyRepository).shouldHaveNoInteractions();
        then(addressQueryService).shouldHaveNoInteractions();
        then(supportScoreCache).should(times(0)).put(any(), any());
    }

    @Test
    @DisplayName("선택한 태그가 없으면 빈 맵을 돌려주고 저장하지 않는다")
    void returnsEmptyMapWhenNothingSelected() {
        // given — 캐시 미스 후 선택 마스크 0
        given(supportScoreCache.find(any())).willReturn(Optional.empty());

        // when
        Map<SigunguCode, Score> result = service.scoresFor(0);

        // then — 선택이 없으면 시군구 조회도, 캐시 저장도 없다
        assertThat(result).isEmpty();
        then(addressQueryService).shouldHaveNoInteractions();
        then(supportPolicyRepository).shouldHaveNoInteractions();
        then(supportScoreCache).should(times(0)).put(any(), any());
    }

    @Test
    @DisplayName("캐시 미스면 선택 태그별로 countByTagForAll 을 태그 수만큼만 호출해 점수를 계산하고 캐시에 저장한다")
    void computesViaMultiFetchAndStoresOnCacheMiss() {
        // given
        List<SigunguCode> codes = List.of(JONGNO, JUNGGU);
        given(supportScoreCache.find(any())).willReturn(Optional.empty());
        given(addressQueryService.getAllSigunguCodes()).willReturn(codes);
        // 종로는 두 태그 모두 정책 있음 → 100점, 중구는 둘 다 없음 → 0점
        given(supportPolicyRepository.countByTagForAll(eq(SupportTag.HOUSING_SUPPORT), eq(codes)))
                .willReturn(Map.of(JONGNO, 1, JUNGGU, 0));
        given(supportPolicyRepository.countByTagForAll(eq(SupportTag.LOAN), eq(codes)))
                .willReturn(Map.of(JONGNO, 2, JUNGGU, 0));

        // when
        Map<SigunguCode, Score> result = service.scoresFor(HOUSING_AND_LOAN);

        // then — 다건 조회 결과가 시군구별로 재조립돼 점수가 나온다
        assertThat(result).containsEntry(JONGNO, Score.of(100)); // (100+100)/2
        assertThat(result).containsEntry(JUNGGU, Score.ZERO);    // (0+0)/2

        // 태그 수(2)만큼만 호출되고, 시군구 개별 countBy 는 쓰지 않는다
        then(supportPolicyRepository).should(times(2)).countByTagForAll(any(), eq(codes));
        then(supportPolicyRepository).should().countByTagForAll(eq(SupportTag.HOUSING_SUPPORT), eq(codes));
        then(supportPolicyRepository).should().countByTagForAll(eq(SupportTag.LOAN), eq(codes));
        then(supportPolicyRepository).should(times(0)).countBy(any(), any());

        // 계산 결과를 키와 함께 캐시에 저장한다
        then(supportScoreCache).should().put(eq(SupportScoreKey.of(HOUSING_AND_LOAN)), eq(result));
    }

    @Test
    @DisplayName("다건 조회로 계산한 결과가 시군구×태그 개별 조회와 동일한 점수를 낸다")
    void multiFetchProducesSameScoresAsPerCombination() {
        // given — 종로: HOUSING 만 있음(1/2 매칭), 중구: 둘 다 있음
        List<SigunguCode> codes = List.of(JONGNO, JUNGGU);
        given(supportScoreCache.find(any())).willReturn(Optional.empty());
        given(addressQueryService.getAllSigunguCodes()).willReturn(codes);
        given(supportPolicyRepository.countByTagForAll(eq(SupportTag.HOUSING_SUPPORT), eq(codes)))
                .willReturn(Map.of(JONGNO, 3, JUNGGU, 1));
        given(supportPolicyRepository.countByTagForAll(eq(SupportTag.LOAN), eq(codes)))
                .willReturn(Map.of(JONGNO, 0, JUNGGU, 2));

        // when
        Map<SigunguCode, Score> result = service.scoresFor(HOUSING_AND_LOAN);

        // then
        assertThat(result).containsEntry(JONGNO, Score.of(50));  // HOUSING 만 → 100/2
        assertThat(result).containsEntry(JUNGGU, Score.of(100)); // 둘 다 → 200/2
    }
}
