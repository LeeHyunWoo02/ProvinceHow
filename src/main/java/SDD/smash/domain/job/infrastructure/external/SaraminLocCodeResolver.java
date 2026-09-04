package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 우리 시군구 코드({@link SigunguCode}, 행정표준 5자리)를 사람인 지역코드({@code loc_cd})로 뒤집는다.
 *
 * <p>스펙 JSON 의 {@code mapping.regionCodes} 는 (사람인 loc_cd → 우리 코드) 방향이라, 스펙을 처음
 * 볼 때 역방향 맵(우리 코드 → loc_cd)을 1회 구성해 이후 O(1) 로 조회한다. 채용공고 목록 조회와
 * 지역 채용 프로필이 <b>공유</b>하는 로직이다.
 *
 * <p><b>역매핑을 찾지 못하면 {@code null}.</b> 호출부는 이때 사람인을 부르지 않고 빈 결과를 돌려줘야
 * 한다 — 전국 공고를 특정 지역인 척 내려주면 안 되기 때문이다. 매핑표가 채워지면 자동으로 동작한다.
 */
@Component
@RequiredArgsConstructor
public class SaraminLocCodeResolver {

    private final SaraminApiSpecLoader specLoader;

    /** 우리 코드 → 사람인 loc_cd 역방향 맵. 원본 매핑이 바뀌면 다시 구성한다. */
    private volatile Map<String, String> reverseIndex;
    /** 역방향 맵을 만든 원본 매핑의 참조. 스펙이 재로드되면 달라져 재구성을 유발한다. */
    private volatile Map<String, String> indexedSource;

    /** @return 사람인 loc_cd, 역매핑이 없으면 {@code null} */
    public String resolve(SigunguCode region) {
        return reverseIndex().get(region.value());
    }

    private Map<String, String> reverseIndex() {
        Map<String, String> source = specLoader.spec().mapping().regionCodes();
        Map<String, String> cached = reverseIndex;
        if (cached == null || indexedSource != source) {
            Map<String, String> built = new HashMap<>();
            // 값(우리 코드)이 중복이면 먼저 만난 loc_cd 를 남긴다(선형 탐색이 첫 매치를 쓰던 것과 같다).
            for (Map.Entry<String, String> entry : source.entrySet()) {
                built.putIfAbsent(entry.getValue(), entry.getKey());
            }
            cached = Map.copyOf(built);
            reverseIndex = cached;
            indexedSource = source;
        }
        return cached;
    }
}
