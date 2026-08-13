package SDD.smash.domain.address.application;

import SDD.smash.domain.address.domain.model.Population;
import SDD.smash.domain.address.domain.port.PopulationRepository;
import SDD.smash.domain.address.domain.port.SigunguRepository;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
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
public class PopulationQueryService {

    private final SigunguRepository sigunguRepository;
    private final PopulationRepository populationRepository;

    /**
     * 해당 시군구의 인구수. 시군구는 존재하지만 인구가 적재되지 않았으면 {@code null} 이다.
     * 존재하지 않는 시군구면 {@code ADDRESS_CODE_NOT_FOUND} 를 던진다.
     */
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
