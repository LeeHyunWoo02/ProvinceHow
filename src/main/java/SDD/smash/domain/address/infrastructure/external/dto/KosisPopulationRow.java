package SDD.smash.domain.address.infrastructure.external.dto;

/**
 * KOSIS 통계자료 응답의 한 행. <b>기술 DTO</b>다.
 *
 * <p>KOSIS 어휘({@code C1}, {@code PRD_DE}, {@code DT}, {@code ITM_ID})는 이 패키지 밖으로 나가지 않는다.
 * 도메인/애플리케이션/표현 계층은 {@code PopulationSnapshot} 만 본다.
 *
 * @param c1     분류값 ID 1. {@code DT_1B040A3}(행정구역(시군구)별) 에서는 행정표준코드다.
 *               전국은 {@code 00}, 시도는 2자리, 시군구는 5자리로 온다
 * @param c1Nm   분류값 명(예: "종로구"). <b>매핑에 쓰지 않는다</b> — 명칭 유사도 매핑 금지
 * @param itmId  항목 ID. 총인구수는 {@code T20}
 * @param prdDe  수록시점. 월 주기이면 {@code yyyyMM}
 * @param dt     수치값. 콤마가 섞여 오거나 통계부호(-, …)가 올 수 있다
 */
public record KosisPopulationRow(String c1, String c1Nm, String itmId, String prdDe, String dt) {
}
