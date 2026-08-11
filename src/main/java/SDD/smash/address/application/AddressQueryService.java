package SDD.smash.address.application;

import SDD.smash.address.application.dto.RegionCodeView;
import SDD.smash.address.application.dto.SidoView;
import SDD.smash.address.application.dto.SigunguView;
import SDD.smash.address.application.port.in.AddressQueryUseCase;
import SDD.smash.address.domain.port.RegionCodeQuery;
import SDD.smash.address.domain.port.SidoRepository;
import SDD.smash.address.domain.port.SigunguRepository;
import SDD.smash.common.domain.model.SidoCode;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 행정구역 조회 유스케이스.
 *
 * <p>As-Is 의 {@code AddressVerifyService} 와 {@code CodeService} 의 행정구역 부분을 옮긴 것이다.
 * 코드 형식 검증은 값 객체({@code SidoCode}/{@code SigunguCode}) 생성자가 흡수했고,
 * 여기에는 "존재 여부" 검증만 남는다.
 */
@Service
@RequiredArgsConstructor
public class AddressQueryService implements AddressQueryUseCase {

    private final SidoRepository sidoRepository;
    private final SigunguRepository sigunguRepository;
    private final RegionCodeQuery regionCodeQuery;

    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<SidoView> getAllSidos() {
        return sidoRepository.findAll().stream()
                .map(SidoView::from)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<SigunguView> getSigungusBySido(SidoCode sidoCode) {
        if (!sidoRepository.existsBy(sidoCode)) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 지역코드");
        }
        return sigunguRepository.findAllBy(sidoCode).stream()
                .map(SigunguView::from)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<SigunguCode> getAllSigunguCodes() {
        return sigunguRepository.findAllCodes();
    }

    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public List<RegionCodeView> getAllRegionCodes() {
        return regionCodeQuery.findAll().stream()
                .map(RegionCodeView::from)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public Optional<RegionCodeView> getRegionCode(SigunguCode sigunguCode) {
        return regionCodeQuery.findBy(sigunguCode).map(RegionCodeView::from);
    }

    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public void checkSigunguExistsOrThrow(SigunguCode sigunguCode) {
        if (!sigunguRepository.existsBy(sigunguCode)) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드");
        }
    }
}
