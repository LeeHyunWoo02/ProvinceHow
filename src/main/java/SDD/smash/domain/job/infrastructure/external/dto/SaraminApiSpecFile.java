package SDD.smash.domain.job.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.Set;

/**
 * 사람인(Saramin) 채용정보 오픈 API 의 응답 구조와 코드 매핑을 코드 밖으로 뺀 설정 파일의 바인딩 대상.
 *
 * <p>워크넷 {@code WorknetApiSpecFile} 과 같은 역할이지만 사람인은 <b>JSON</b> 응답이고
 * 공고당 지역/직종이 <b>단일 값</b>이라 응답 필드가 다르다.
 *
 * <p>사람인 자체 코드({@code loc_cd}, {@code job_cd})는 우리 코드 체계(행정표준 시군구 5자리,
 * KECO 중분류 3자리)와 다르므로 <b>passthrough 를 쓸 수 없다.</b> 매핑 기본값은 전부 false 다.
 *
 * <p>알 수 없는 필드는 무시한다({@code _comment} 같은 주석 키를 허용하기 위함).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaraminApiSpecFile(Request request, Response response, Mapping mapping) {

    public SaraminApiSpecFile {
        request = (request == null) ? Request.defaults() : request;
        response = (response == null) ? Response.defaults() : response;
        mapping = (mapping == null) ? Mapping.defaults() : mapping;
    }

    /**
     * 요청 파라미터명과 상한.
     *
     * @param accessKeyParam 인증키 파라미터명. 사람인은 {@code access-key} 다(확인됨)
     * @param startParam     시작 페이지 파라미터명. {@code start}(0-based) 다(확인됨)
     * @param countParam     페이지당 결과 수 파라미터명. {@code count} 다(확인됨)
     * @param maxCount       1회 최대 결과 수. 사람인 상한은 110 이다(확인됨)
     * @param maxStartPage   당길 최대 페이지 수 상한. <b>1일 500회 호출 제한</b>을 넘지 않게 배치가 운영한다
     * @param extraParams    loc_cd/job_cd/ind_cd 등 고정 필터. 값은 사람인 자체 코드여야 한다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Request(String accessKeyParam,
                          String startParam,
                          String countParam,
                          Integer maxCount,
                          Integer maxStartPage,
                          Map<String, String> extraParams) {

        public Request {
            accessKeyParam = blankTo(accessKeyParam, "access-key");
            startParam = blankTo(startParam, "start");
            countParam = blankTo(countParam, "count");
            maxCount = (maxCount == null || maxCount < 1) ? 110 : Math.min(maxCount, 110);
            maxStartPage = (maxStartPage == null || maxStartPage < 1) ? 1000 : maxStartPage;
            extraParams = (extraParams == null) ? Map.of() : Map.copyOf(extraParams);
        }

        public static Request defaults() {
            return new Request(null, null, null, null, null, null);
        }
    }

    /**
     * 응답 필드 경로. 중첩은 점(.)으로 표현한다(예: {@code position.location.code}).
     *
     * @param rootField         목록/전체건수를 감싼 루트 오브젝트명. {@code jobs} 다(확인됨)
     * @param totalField        전체 건수 필드명. {@code total}(문자열) 이다(확인됨)
     * @param listField         목록 배열 필드명. {@code job} 이다(확인됨)
     * @param postingIdField    공고 식별자 필드명. {@code id} 다(확인됨)
     * @param regionCodePath    지역코드 경로. {@code position.location.code}(사람인 loc_cd)
     * @param jobCodePath       직종코드 경로. {@code position.job-code.code}(사람인 job_cd)
     * @param errorField        오류 판정용 필드명. 응답에 있으면 예외를 던진다(없으면 무시)
     * @param errorMessageField 오류 메시지 필드명
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String rootField,
                           String totalField,
                           String listField,
                           String postingIdField,
                           String regionCodePath,
                           String jobCodePath,
                           String errorField,
                           String errorMessageField) {

        public Response {
            rootField = blankTo(rootField, "jobs");
            totalField = blankTo(totalField, "total");
            listField = blankTo(listField, "job");
            postingIdField = blankTo(postingIdField, "id");
            regionCodePath = blankTo(regionCodePath, "position.location.code");
            jobCodePath = blankTo(jobCodePath, "position.job-code.code");
            errorField = blankTo(errorField, "error");
            errorMessageField = blankTo(errorMessageField, "message");
        }

        public static Response defaults() {
            return new Response(null, null, null, null, null, null, null, null);
        }
    }

    /**
     * 코드 체계 변환.
     *
     * <p><b>명칭 유사도 자동 매핑은 하지 않는다.</b> 코드 대 코드만 옮긴다.
     *
     * @param regionCodePassthrough 사람인 지역코드를 시군구 5자리로 그대로 쓸 수 있는가. <b>사람인은 false 여야 한다</b>
     * @param regionCodes           사람인 loc_cd → 시군구 5자리 명시 매핑(passthrough 보다 우선)
     * @param jobCodePassthrough     사람인 직종코드를 KECO 중분류 3자리로 그대로 쓸 수 있는가. <b>사람인은 false 여야 한다</b>
     * @param jobCodes              사람인 job_cd → 중분류 3자리 명시 매핑(passthrough 보다 우선)
     * @param ignoredRegionCodes    의도적으로 버리는 코드. 여기 있는 코드는 매핑 실패 경고를 내지 않는다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mapping(Boolean regionCodePassthrough,
                          Map<String, String> regionCodes,
                          Boolean jobCodePassthrough,
                          Map<String, String> jobCodes,
                          Set<String> ignoredRegionCodes) {

        public Mapping {
            // 워크넷과 달리 사람인 기본값은 false 다 - 자체 코드라 그대로 쓸 수 없다.
            regionCodePassthrough = (regionCodePassthrough != null) && regionCodePassthrough;
            jobCodePassthrough = (jobCodePassthrough != null) && jobCodePassthrough;
            regionCodes = (regionCodes == null) ? Map.of() : Map.copyOf(regionCodes);
            jobCodes = (jobCodes == null) ? Map.of() : Map.copyOf(jobCodes);
            ignoredRegionCodes = (ignoredRegionCodes == null) ? Set.of()
                    : Set.copyOf(ignoredRegionCodes);
        }

        public static Mapping defaults() {
            return new Mapping(null, null, null, null, null);
        }
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    public static SaraminApiSpecFile defaults() {
        return new SaraminApiSpecFile(null, null, null);
    }
}
