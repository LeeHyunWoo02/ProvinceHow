package SDD.smash.domain.infra.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.infra.application.dto.IndustryCountView;
import SDD.smash.domain.infra.application.dto.MajorInfraSummaryView;
import SDD.smash.domain.infra.domain.port.InfraMajorSummaryRepository;
import SDD.smash.domain.infra.domain.port.RegionInfraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 인프라 조회 유스케이스. {@code recommendation} 이 infra 를 호출하는 통로다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InfraQueryService {

    private final AddressQueryService addressQueryService;
    private final InfraMajorSummaryRepository infraMajorSummaryRepository;
    private final RegionInfraRepository regionInfraRepository;

    /**
     * 대분류별 요약을 한 번의 쿼리로 조회한다. 데이터가 없는 대분류는 결과에서 빠진다
     * (예외를 던지지 않는다 — As-Is 도 "적재된 데이터의 문제"로 보고 건너뛰었다).
     * 출력 순서는 Major enum 순서를 유지한다.
     */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<MajorInfraSummaryView> getMajorInfraSummaries(SigunguCode sigunguCode) {
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        List<MajorInfraSummaryView> result = infraMajorSummaryRepository.findAllBy(sigunguCode).stream()
                .sorted(Comparator.comparingInt(summary -> summary.major().ordinal()))
                .map(MajorInfraSummaryView::from)
                .toList();
        if (result.isEmpty()) {
            log.warn("{}지역의 인프라 정보가 없습니다.", sigunguCode.value());
        }
        return result;
    }

    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<IndustryCountView> getInfraDetails(SigunguCode sigunguCode) {
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        return regionInfraRepository.findBy(sigunguCode).industryCounts().stream()
                .map(IndustryCountView::from)
                .toList();
    }
}
