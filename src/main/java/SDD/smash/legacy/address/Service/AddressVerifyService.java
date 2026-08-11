package SDD.smash.legacy.address.Service;

import SDD.smash.legacy.address.Repository.SidoRepository;
import SDD.smash.legacy.address.Repository.SigunguRepository;
import SDD.smash.common.exception.ErrorCode;
import SDD.smash.common.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddressVerifyService {

    private final SidoRepository sidoRepository;
    private final SigunguRepository sigunguRepository;

    public void checkSigunguCodeOrThrow(String sigunguCode)
    {
        if(!sigunguRepository.existsBySigunguCode(sigunguCode))
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드");
    }
}
