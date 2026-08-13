package SDD.smash.domain.infra.infrastructure.batch;

/**
 * 인프라 개수를 어디서 얻을지. {@code infra.collect.source} 프로퍼티가 정한다.
 *
 * <p>기본은 {@link #API} 다 — 공식 엔드포인트이고 유지 보장이 있는 유일한 경로이기 때문이다.
 */
public enum InfraCollectSource {

    /**
     * 공식 data.go.kr 업종별 API. <b>기본값.</b>
     *
     * <p>{@code numOfRows} 상한 100 + 개발계정 10,000회/일이라 <b>전국 전 업종 수집은 불가능</b>하다.
     * 대상 업종·자치단체를 좁혀 쓰거나 {@link #BULK_CSV} 를 쓴다.
     */
    API,

    /**
     * 무인증 벌크 CSV({@code file.localdata.go.kr}). 자치단체·업종당 요청 1회로 전량을 받는다.
     *
     * <p>공식 유지 기간/SLA 가 <b>미확인</b>이라 기본값이 아니다. 시드 구축처럼
     * "한 번에 전국을 채워야 하는" 상황에서 명시적으로 켠다.
     */
    BULK_CSV,

    /**
     * 저장소의 {@code data/legacy/infra.csv} 를 그대로 읽는다. 외부 호출이 없다.
     *
     * <p>구 LOCALDATA {@code opnSvcId} 기준이라 <b>업종 마스터에 매핑이 등록된 것만</b> 적재된다.
     * 데이터가 언제 만들어졌는지 알 수 없어 <b>기본 경로가 되어서는 안 된다.</b>
     * 오프라인 개발·회귀 확인용이다.
     */
    LEGACY_CSV
}
