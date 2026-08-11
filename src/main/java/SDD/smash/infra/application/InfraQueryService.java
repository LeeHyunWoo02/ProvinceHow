package SDD.smash.infra.application;

import SDD.smash.address.application.port.in.AddressQueryUseCase;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.infra.application.dto.IndustryCountView;
import SDD.smash.infra.application.dto.MajorInfraSummaryView;
import SDD.smash.infra.application.port.in.InfraQueryUseCase;
import SDD.smash.infra.domain.model.Major;
import SDD.smash.infra.domain.port.InfraMajorSummaryRepository;
import SDD.smash.infra.domain.port.RegionInfraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 인프라 조회 유스케이스. As-Is {@code InfraService} 를 옮긴 것이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InfraQueryService implements InfraQueryUseCase {

    private final AddressQueryUseCase addressQueryUseCase;
    private final InfraMajorSummaryRepository infraMajorSummaryRepository;
    private final RegionInfraRepository regionInfraRepository;

    /**
     * 대분류(4종)를 순회하며 조회한다. 해당 대분류의 인프라 정보가 없으면
     * <b>예외를 던지지 않고 로그만 남긴 뒤 목록에서 뺀다</b> — As-Is 도 "적재된 데이터의 문제"로
     * 보고 건너뛰었다.
     */
    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<MajorInfraSummaryView> getMajorInfraSummaries(SigunguCode sigunguCode) {
        addressQueryUseCase.checkSigunguExistsOrThrow(sigunguCode);

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

    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<IndustryCountView> getInfraDetails(SigunguCode sigunguCode) {
        addressQueryUseCase.checkSigunguExistsOrThrow(sigunguCode);

        return regionInfraRepository.findBy(sigunguCode).industryCounts().stream()
                .map(IndustryCountView::from)
                .toList();
    }
}
