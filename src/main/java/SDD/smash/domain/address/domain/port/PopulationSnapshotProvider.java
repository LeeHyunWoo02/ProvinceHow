package SDD.smash.domain.address.domain.port;

import SDD.smash.domain.address.domain.model.PopulationSnapshot;

import java.time.YearMonth;
import java.util.List;

/**
 * 외부 통계에서 시군구 인구를 받아오는 out-port.
 *
 * <p>구현이 어떤 기관의 어떤 통계표를 쓰는지, 인증을 어떻게 하는지는 이 계약에 드러나지 않는다.
 * 도메인은 "기준월을 주면 그 달의 시군구 인구를 받는다"만 안다.
 *
 * <p>반환 목록에는 <b>시군구 단위 행만</b> 들어 있다. 전국 합계·시도 합계·읍면동처럼
 * 시군구가 아닌 행을 걸러내는 것은 구현의 책임이다(시군구 코드는 5자리라는 사실이
 * {@code SigunguCode} 의 불변식이므로 그 밖의 행은 애초에 이 타입이 될 수 없다).
 */
public interface PopulationSnapshotProvider {

    /**
     * 자료를 받을 수 있는 상태인지. 인증 정보 같은 필수 설정이 비어 있으면 {@code false} 다.
     *
     * <p>{@code false} 인데 {@code fetch...} 를 부르면 구현은 <b>외부 호출 없이</b> 실패한다.
     */
    boolean isAvailable();

    /** 지정한 기준월의 자료. 그 달 자료가 아직 없으면 빈 목록이다. */
    List<PopulationSnapshot> fetch(YearMonth statisticsMonth);

    /**
     * {@code notAfter} 이하의 가장 최근 확정 기준월 자료.
     *
     * <p>이번 달 자료가 아직 공표되지 않았으면 직전 확정 월로 내려간다.
     * 그래도 없으면 빈 목록이다. 반환된 목록의 모든 원소는 같은 기준월을 갖는다.
     */
    List<PopulationSnapshot> fetchLatestNotAfter(YearMonth notAfter);
}
