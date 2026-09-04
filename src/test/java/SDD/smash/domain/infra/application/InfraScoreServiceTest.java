package SDD.smash.domain.infra.application;

import SDD.smash.domain.infra.domain.model.InfraScoreKey;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.domain.model.RegionMajorScore;
import SDD.smash.domain.infra.domain.port.InfraScoreCache;
import SDD.smash.domain.infra.domain.port.RegionMajorScoreRepository;
import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * InfraScoreService 유스케이스 테스트. 저장소·캐시 포트를 목킹한다(구현체 아님).
 * 정책({@link SDD.smash.domain.infra.domain.service.InfraScorePolicy})은 서비스가 직접
 * new 로 들고 있으므로 목킹하지 않고 실제 계산을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class InfraScoreServiceTest {

    @Mock
    RegionMajorScoreRepository regionMajorScoreRepository;

    @Mock
    InfraScoreCache infraScoreCache;

    @InjectMocks
    InfraScoreService service;

    @Test
    @DisplayName("캐시가 있으면 저장소를 조회하지 않는다")
    void returnsCachedScoresWithoutQueryingRepository() {
        // given
        Map<SigunguCode, Score> cached = Map.of(SigunguCode.of("11110"), Score.of(100));
        given(infraScoreCache.find(any(InfraScoreKey.class))).willReturn(Optional.of(cached));

        // when
        Map<SigunguCode, Score> result = service.scoresFor(1);

        // then
        assertThat(result).containsEntry(SigunguCode.of("11110"), Score.of(100));
        then(regionMajorScoreRepository).shouldHaveNoInteractions();
        then(infraScoreCache).should(never()).put(any(), any());
    }

    @Test
    @DisplayName("캐시 미스면 저장소 조회 후 계산 결과를 캐시에 저장한다")
    void storesComputedScoresOnCacheMiss() {
        // given
        given(infraScoreCache.find(any(InfraScoreKey.class))).willReturn(Optional.empty());
        given(regionMajorScoreRepository.findAllBy(anySet()))
                .willReturn(List.of(new RegionMajorScore(SigunguCode.of("11110"), Major.LIFE, 80.0)));

        // when — LIFE 만 선택(비트마스크 1)
        Map<SigunguCode, Score> result = service.scoresFor(1);

        // then — 선택 대분류 1개라 평균이 곧 합계(80점)
        assertThat(result).containsEntry(SigunguCode.of("11110"), Score.of(80));
        then(regionMajorScoreRepository).should().findAllBy(Set.of(Major.LIFE));
        then(infraScoreCache).should().put(any(InfraScoreKey.class), any());
    }

    @Test
    @DisplayName("선택한 대분류가 없으면 빈 맵을 반환하고 저장소 조회도 캐시 저장도 하지 않는다")
    void returnsEmptyMapWithoutQueryingWhenNoMajorSelected() {
        // given — 캐시 미스지만 infraChoice 0 이라 선택 대분류가 없다
        given(infraScoreCache.find(any(InfraScoreKey.class))).willReturn(Optional.empty());

        // when
        Map<SigunguCode, Score> result = service.scoresFor(0);

        // then
        assertThat(result).isEmpty();
        then(regionMajorScoreRepository).shouldHaveNoInteractions();
        then(infraScoreCache).should(never()).put(any(), any());
    }

    @Test
    @DisplayName("infraChoice 가 null 이면 선택 없음으로 보고 빈 맵을 반환한다")
    void treatsNullChoiceAsNoSelection() {
        // given
        given(infraScoreCache.find(any(InfraScoreKey.class))).willReturn(Optional.empty());

        // when
        Map<SigunguCode, Score> result = service.scoresFor(null);

        // then
        assertThat(result).isEmpty();
        then(regionMajorScoreRepository).shouldHaveNoInteractions();
        then(infraScoreCache).should(never()).put(any(), any());
    }
}
