package SDD.smash.dwelling.application.port.in;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.dwelling.application.dto.DwellingInfo;
import SDD.smash.dwelling.application.dto.DwellingSimpleInfo;

/**
 * 전월세 시세 조회 in-port. {@code recommendation} 이 dwelling 을 호출하는 통로다.
 *
 * <p>반환이 {@code null} 이면 "시군구는 있으나 실거래 데이터가 없다"는 뜻이다.
 * 존재하지 않는 시군구는 {@code ADDRESS_CODE_NOT_FOUND} 예외다. As-Is 계약을 그대로 옮겼다.
 */
public interface DwellingQueryUseCase {

    DwellingSimpleInfo getDwellingSimpleInfo(SigunguCode code);

    DwellingInfo getDwellingInfo(SigunguCode code);
}
