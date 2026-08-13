package SDD.smash.domain.infra.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.infra.application.dto.IndustryCountView;
import SDD.smash.domain.infra.application.dto.MajorInfraSummaryView;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.domain.port.InfraMajorSummaryRepository;
import SDD.smash.domain.infra.domain.port.RegionInfraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
     * 대분류(4종)를 순회하며 조회한다. 해당 대분류의 인프라 정보가 없으면
     * <b>예외를 던지지 않고 로그만 남긴 뒤 목록에서 뺀다</b> — As-Is 도 "적재된 데이터의 문제"로
     * 보고 건너뛰었다.
     */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<MajorInfraSummaryView> getMajorInfraSummaries(SigunguCode sigunguCode) {
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        List<MajorInfraSummaryView> result = new ArrayList<>();
        for (Major major : Major.values()) {
            Optional<MajorInfraSummaryView> summary = infraMajorSummaryRepository.findBy(sigunguCode, major)
                    .map(MajorInfraSummaryView::from);
            if (summary.isPresent()) {
                result.add(summary.get());
                continue;
            }
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
