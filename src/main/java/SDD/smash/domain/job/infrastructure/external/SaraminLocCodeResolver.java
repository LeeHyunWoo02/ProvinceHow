package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 우리 시군구 코드({@link SigunguCode}, 행정표준 5자리)를 사람인 지역코드({@code loc_cd})로 뒤집는다.
 *
 * <p>스펙 JSON 의 {@code mapping.regionCodes} 는 (사람인 loc_cd → 우리 코드) 방향이라, 값이 우리
 * 코드와 같은 항목의 키를 찾는다. 채용공고 목록 조회와 지역 채용 프로필이 <b>공유</b>하는 로직이다.
 *
 * <p><b>역매핑을 찾지 못하면 {@code null}.</b> 호출부는 이때 사람인을 부르지 않고 빈 결과를 돌려줘야
 * 한다 — 전국 공고를 특정 지역인 척 내려주면 안 되기 때문이다. 매핑표가 채워지면 자동으로 동작한다.
 */
@Component
@RequiredArgsConstructor
public class SaraminLocCodeResolver {

    private final SaraminApiSpecLoader specLoader;

    /** @return 사람인 loc_cd, 역매핑이 없으면 {@code null} */
    public String resolve(SigunguCode region) {
        String ours = region.value();
        for (Map.Entry<String, String> entry : specLoader.spec().mapping().regionCodes().entrySet()) {
            if (ours.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
