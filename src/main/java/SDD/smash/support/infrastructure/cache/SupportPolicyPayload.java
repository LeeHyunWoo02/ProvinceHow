package SDD.smash.support.infrastructure.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Redis 역직렬화용 기술 DTO. 도메인({@code SupportPolicy})으로 새어나가지 않는다.
 * Jackson 이 기본 생성자 + setter 로 역직렬화하므로 record 를 쓰지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportPolicyPayload {

    private String name;
    private String applyUrl;
    private String keyword;
}
