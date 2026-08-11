package SDD.smash.address.application;

import SDD.smash.address.application.port.in.PopulationQueryUseCase;
import SDD.smash.address.domain.model.Population;
import SDD.smash.address.domain.port.PopulationRepository;
import SDD.smash.address.domain.port.SigunguRepository;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인구 조회 유스케이스. As-Is 의 {@code PopulationService} 를 옮긴 것이다.
 *
 * <p>"코드가 없다"(예외)와 "코드는 있는데 인구 데이터가 없다"({@code null})를 구분하는
 * As-Is 동작을 그대로 유지한다.
 */
@Service
@RequiredArgsConstructor
public class PopulationQueryService implements PopulationQueryUseCase {

    private final SigunguRepository sigunguRepository;
    private final PopulationRepository populationRepository;

    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public Integer getPopulation(SigunguCode sigunguCode) {
        if (!sigunguRepository.existsBy(sigunguCode)) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드");
        }
        return populationRepository.findBy(sigunguCode)
                .map(Population::count)
                .orElse(null);
    }
}
