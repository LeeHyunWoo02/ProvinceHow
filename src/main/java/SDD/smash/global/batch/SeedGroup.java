package SDD.smash.global.batch;

/**
 * 시드 Step 의 성격 구분. 실패했을 때의 처리가 완전히 다르다.
 *
 * <ul>
 *   <li>{@link #ESSENTIAL} — 필수 기준 데이터. 실패하면 Job 을 FAILED 로 끝내고
 *       애플리케이션을 준비 완료로 표시하지 않는다.</li>
 *   <li>{@link #EXTERNAL} — 외부 갱신 데이터. 실패해도 Job 전체를 죽이지 않고
 *       "미적재" 사실만 남긴다. 다음 기준일/기준월에 다시 시도한다.</li>
 * </ul>
 */
public enum SeedGroup {

    ESSENTIAL,
    EXTERNAL;

    public boolean isEssential() {
        return this == ESSENTIAL;
    }
}
