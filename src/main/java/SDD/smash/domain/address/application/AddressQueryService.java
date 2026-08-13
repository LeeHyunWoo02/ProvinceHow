package SDD.smash.domain.address.application;

import SDD.smash.domain.address.application.dto.RegionCodeView;
import SDD.smash.domain.address.application.dto.SidoView;
import SDD.smash.domain.address.application.dto.SigunguView;
import SDD.smash.domain.address.domain.port.RegionCodeQuery;
import SDD.smash.domain.address.domain.port.SidoRepository;
import SDD.smash.domain.address.domain.port.SigunguRepository;
import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 행정구역 조회 유스케이스.
 *
 * <p>다른 컨텍스트(recommendation, job, dwelling, infra, support)가 address 를 호출하는
 * 유일한 통로다. address 의 Aggregate 나 Repository 를 직접 쓰지 않는다.
 *
 * <p>코드 형식 검증은 값 객체({@code SidoCode}/{@code SigunguCode}) 생성자가 흡수했고,
 * 여기에는 "존재 여부" 검증만 남는다.
 */
@Service
@RequiredArgsConstructor
public class AddressQueryService {

    private final SidoRepository sidoRepository;
    private final SigunguRepository sigunguRepository;
    private final RegionCodeQuery regionCodeQuery;

    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<SidoView> getAllSidos() {
        return sidoRepository.findAll().stream()
                .map(SidoView::from)
                .toList();
    }

    /** 해당 시도가 없으면 {@code ADDRESS_CODE_NOT_FOUND} 를 던진다. */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<SigunguView> getSigungusBySido(SidoCode sidoCode) {
        if (!sidoRepository.existsBy(sidoCode)) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 지역코드");
        }
        return sigunguRepository.findAllBy(sidoCode).stream()
                .map(SigunguView::from)
                .toList();
    }

    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<SigunguCode> getAllSigunguCodes() {
        return sigunguRepository.findAllCodes();
    }

    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<RegionCodeView> getAllRegionCodes() {
        return regionCodeQuery.findAll().stream()
                .map(RegionCodeView::from)
                .toList();
    }

    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public Optional<RegionCodeView> getRegionCode(SigunguCode sigunguCode) {
        return regionCodeQuery.findBy(sigunguCode).map(RegionCodeView::from);
    }

    /** 존재하지 않는 시군구면 {@code ADDRESS_CODE_NOT_FOUND} 를 던진다. */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public void checkSigunguExistsOrThrow(SigunguCode sigunguCode) {
        if (!sigunguRepository.existsBy(sigunguCode)) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드");
        }
    }
}
