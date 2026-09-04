package SDD.smash.domain.dwelling.application;

import SDD.smash.domain.dwelling.domain.model.DwellingMarket;
import SDD.smash.domain.dwelling.domain.model.DwellingScoreKey;
import SDD.smash.domain.dwelling.domain.model.DwellingType;
import SDD.smash.domain.dwelling.domain.model.RentStat;
import SDD.smash.domain.dwelling.domain.port.DwellingMarketRepository;
import SDD.smash.domain.dwelling.domain.port.DwellingScoreCache;
import SDD.smash.global.domain.model.Money;
import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * 주거 적합도 점수 유스케이스. 포트(저장소·캐시)만 목킹한다.
 *
 * <p>{@code RedisTemplate} 이 아니라 {@link DwellingScoreCache} 포트를 목킹하는 것이
 * 포트화의 이득이다. 캐시 히트/미스 두 경로와 예산 구간화를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DwellingScoreServiceTest {

    private static final SigunguCode CODE = SigunguCode.of("11110");

    @Mock
    DwellingMarketRepository dwellingMarketRepository;

    @Mock
    DwellingScoreCache dwellingScoreCache;

    @InjectMocks
    DwellingScoreService dwellingScoreService;

    @Captor
    ArgumentCaptor<DwellingScoreKey> keyCaptor;

    @Test
    @DisplayName("캐시가 히트하면 저장소를 조회하지 않고 캐시 값을 그대로 반환한다")
    void returnsCachedScoresWithoutQueryingRepository() {
        // given
        Map<SigunguCode, Score> cached = Map.of(CODE, Score.of(100));
        given(dwellingScoreCache.find(any())).willReturn(Optional.of(cached));

        // when
        Map<SigunguCode, Score> result = dwellingScoreService.scoresFor(DwellingType.MONTHLY, Money.of(60));

        // then
        assertThat(result).containsEntry(CODE, Score.of(100));
        then(dwellingMarketRepository).shouldHaveNoInteractions();
        then(dwellingScoreCache).should().find(any());
        then(dwellingScoreCache).should(org.mockito.Mockito.never()).put(any(), any());
    }

    @Test
    @DisplayName("캐시 미스면 전 시군구 시세를 읽어 정책을 적용하고 결과를 캐시에 저장한다")
    void computesAndStoresScoresOnCacheMiss() {
        // given - 시세 중앙값이 예산과 같으면 만점이다
        given(dwellingScoreCache.find(any())).willReturn(Optional.empty());
        given(dwellingMarketRepository.findAll()).willReturn(List.of(monthlyMarket("11110", 60)));

        // when
        Map<SigunguCode, Score> result = dwellingScoreService.scoresFor(DwellingType.MONTHLY, Money.of(60));

        // then
        assertThat(result).containsEntry(CODE, Score.of(100));
        then(dwellingScoreCache).should().put(any(), any());
    }

    @Test
    @DisplayName("실거래가 없는 시군구도 0점으로 결과에 포함된다")
    void includesRegionsWithoutMarketDataAsZero() {
        // given - 월세 실거래가 없는 시군구
        given(dwellingScoreCache.find(any())).willReturn(Optional.empty());
        given(dwellingMarketRepository.findAll()).willReturn(List.of(emptyMarket("11110")));

        // when
        Map<SigunguCode, Score> result = dwellingScoreService.scoresFor(DwellingType.MONTHLY, Money.of(60));

        // then
        assertThat(result).containsEntry(CODE, Score.ZERO);
    }

    @Test
    @DisplayName("예산은 캐시 키 생성 시 구간으로 보정되어 저장소/캐시에 전달된다")
    void normalizesBudgetIntoCacheKey() {
        // given - 63 은 월세 10만원 단위로 보정되어 60 이 된다
        given(dwellingScoreCache.find(any())).willReturn(Optional.empty());
        given(dwellingMarketRepository.findAll()).willReturn(List.of(monthlyMarket("11110", 60)));

        // when
        dwellingScoreService.scoresFor(DwellingType.MONTHLY, Money.of(63));

        // then
        then(dwellingScoreCache).should().find(keyCaptor.capture());
        DwellingScoreKey usedKey = keyCaptor.getValue();
        assertThat(usedKey.type()).isEqualTo(DwellingType.MONTHLY);
        assertThat(usedKey.normalizedBudget()).isEqualTo(Money.of(60));
    }

    /** 월세 중앙값만 채운 시세. 전세는 비운다. */
    private DwellingMarket monthlyMarket(String code, int monthlyMedian) {
        return DwellingMarket.reconstitute(
                SigunguCode.of(code), RentStat.of(null, monthlyMedian), RentStat.EMPTY);
    }

    /** 실거래가 전혀 없는 시세. */
    private DwellingMarket emptyMarket(String code) {
        return DwellingMarket.reconstitute(SigunguCode.of(code), RentStat.EMPTY, RentStat.EMPTY);
    }
}
