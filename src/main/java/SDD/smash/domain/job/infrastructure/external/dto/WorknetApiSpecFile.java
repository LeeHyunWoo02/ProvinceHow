package SDD.smash.domain.job.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 워크넷 채용정보 API 의 <b>미확인 스펙</b>을 코드 밖으로 뺀 설정 파일의 바인딩 대상.
 *
 * <p>공식 개발명세서는 인증키 발급자에게만 열려 있어 다음 항목을 문서로 확정하지 못했다
 * (docs/worknet-job-api.md 의 "미확인" 표):
 * <ul>
 *   <li>응답 {@code <wanted>} 안의 <b>지역코드/직종코드 필드명</b></li>
 *   <li>워크넷 지역코드가 행정표준코드 시군구 5자리와 같은 체계인지</li>
 *   <li>다중 지역/다중 직종을 한 필드에 어떤 구분자로 넣는지</li>
 * </ul>
 * 그래서 <b>추측한 값을 자바 코드에 상수로 박지 않고</b> 이 파일에 모아 두고,
 * 인증키를 받은 뒤 실제 응답을 보고 파일만 고치면 되도록 했다.
 *
 * <p>알 수 없는 필드는 무시한다({@code _comment} 같은 주석 키를 허용하기 위함).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorknetApiSpecFile(Request request, Response response, Mapping mapping) {

    public WorknetApiSpecFile {
        request = (request == null) ? Request.defaults() : request;
        response = (response == null) ? Response.defaults() : response;
        mapping = (mapping == null) ? Mapping.defaults() : mapping;
    }

    /**
     * 요청 파라미터명과 고정값.
     *
     * @param authKeyParam   인증키 파라미터명. 워크넷 엔드포인트는 {@code authKey} 다(확인됨)
     * @param callTypeParam  호출유형 파라미터명 / {@code callTypeList} 목록조회 값
     * @param returnTypeParam 반환형식 파라미터명 / {@code returnTypeValue} 값. XML 만 허용된다(확인됨)
     * @param startPageParam 시작페이지 파라미터명
     * @param displayParam   출력건수 파라미터명
     * @param maxDisplay     1회 최대 출력건수
     * @param maxStartPage   최대 시작페이지
     * @param extraParams    지역/직종 등 조건을 고정으로 걸고 싶을 때 쓰는 추가 파라미터
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Request(String authKeyParam,
                          String callTypeParam,
                          String callTypeList,
                          String returnTypeParam,
                          String returnTypeValue,
                          String startPageParam,
                          String displayParam,
                          Integer maxDisplay,
                          Integer maxStartPage,
                          Map<String, String> extraParams) {

        public Request {
            authKeyParam = blankTo(authKeyParam, "authKey");
            callTypeParam = blankTo(callTypeParam, "callTp");
            callTypeList = blankTo(callTypeList, "L");
            returnTypeParam = blankTo(returnTypeParam, "returnType");
            returnTypeValue = blankTo(returnTypeValue, "XML");
            startPageParam = blankTo(startPageParam, "startPage");
            displayParam = blankTo(displayParam, "display");
            maxDisplay = (maxDisplay == null || maxDisplay < 1) ? 100 : maxDisplay;
            maxStartPage = (maxStartPage == null || maxStartPage < 1) ? 1000 : maxStartPage;
            extraParams = (extraParams == null) ? Map.of() : Map.copyOf(extraParams);
        }

        public static Request defaults() {
            return new Request(null, null, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * 응답 요소명.
     *
     * @param listElement      목록 요소명. {@code wanted} 다(확인됨)
     * @param totalElement     전체 건수 요소명. {@code total} 이다(확인됨)
     * @param messageElement   오류 메시지 요소명. {@code message} 다(확인됨)
     * @param messageCodeElement 오류 코드 요소명. {@code messageCd} 다(확인됨)
     * @param postingIdField   구인인증번호 필드명. {@code wantedAuthNo} 다(확인됨)
     * @param regionFields     지역코드가 들어 있는 후보 필드명들. <b>미확인</b> — 앞에서부터 먼저 값이 있는 것을 쓴다
     * @param jobCodeFields    직종코드가 들어 있는 후보 필드명들. <b>미확인</b>
     * @param multiValueDelimiters 한 필드에 여러 코드가 들어올 때의 구분자 후보. <b>미확인</b>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String listElement,
                           String totalElement,
                           String messageElement,
                           String messageCodeElement,
                           String postingIdField,
                           List<String> regionFields,
                           List<String> jobCodeFields,
                           List<String> multiValueDelimiters) {

        public Response {
            listElement = blankTo(listElement, "wanted");
            totalElement = blankTo(totalElement, "total");
            messageElement = blankTo(messageElement, "message");
            messageCodeElement = blankTo(messageCodeElement, "messageCd");
            postingIdField = blankTo(postingIdField, "wantedAuthNo");
            regionFields = emptyTo(regionFields, List.of("regionCd", "region"));
            jobCodeFields = emptyTo(jobCodeFields, List.of("jobsCd", "occupation"));
            multiValueDelimiters = emptyTo(multiValueDelimiters, List.of(",", "|"));
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
     * @param regionCodePassthrough 워크넷 지역코드를 시군구 5자리로 그대로 쓸 수 있는가
     * @param regionCodes           워크넷 지역코드 → 시군구 5자리 명시 매핑(passthrough 보다 우선)
     * @param jobCodePassthrough    워크넷 직종코드를 KECO 중분류 3자리로 그대로 쓸 수 있는가
     * @param jobCodes              워크넷 직종코드 → 중분류 3자리 명시 매핑(passthrough 보다 우선)
     * @param ignoredRegionCodes    시도 단위·전국 등 시군구로 확정할 수 없어 <b>의도적으로 버리는</b> 코드.
     *                              여기 있는 코드는 매핑 실패 경고를 내지 않는다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mapping(Boolean regionCodePassthrough,
                          Map<String, String> regionCodes,
                          Boolean jobCodePassthrough,
                          Map<String, String> jobCodes,
                          Set<String> ignoredRegionCodes) {

        public Mapping {
            regionCodePassthrough = (regionCodePassthrough == null) || regionCodePassthrough;
            jobCodePassthrough = (jobCodePassthrough == null) || jobCodePassthrough;
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

    private static <T> List<T> emptyTo(List<T> value, List<T> fallback) {
        return (value == null || value.isEmpty()) ? fallback : List.copyOf(value);
    }

    public static WorknetApiSpecFile defaults() {
        return new WorknetApiSpecFile(null, null, null);
    }
}
