package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.infrastructure.external.dto.WorknetApiSpecFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 워크넷 API 스펙/코드 매핑 설정 파일을 읽는다.
 *
 * <p>파일이 없거나 깨져 있어도 <b>기동을 막지 않는다.</b> 전부 기본값으로 떨어지고 경고만 남긴다 —
 * 이 설정은 배치 하나의 관심사이고, 애플리케이션 전체가 뜨지 못할 이유가 되면 안 된다.
 *
 * <p>Jackson 2({@code com.fasterxml.jackson}) 를 쓴다. Spring Boot 3.5.7 이 관리하는 버전이다.
 */
@Component
@Slf4j
public class WorknetApiSpecLoader {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String specPath;

    private volatile WorknetApiSpecFile spec;

    public WorknetApiSpecLoader(ObjectMapper objectMapper,
                                ResourceLoader resourceLoader,
                                @Value("${worknet.job.spec-path:classpath:worknet/worknet-job-api.json}")
                                String specPath) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.specPath = specPath;
    }

    public WorknetApiSpecFile spec() {
        WorknetApiSpecFile loaded = spec;
        if (loaded == null) {
            synchronized (this) {
                loaded = spec;
                if (loaded == null) {
                    loaded = load();
                    spec = loaded;
                }
            }
        }
        return loaded;
    }

    private WorknetApiSpecFile load() {
        Resource resource = resourceLoader.getResource(specPath);
        if (!resource.exists()) {
            log.warn("[worknet] 스펙 파일이 없어 기본값으로 동작한다. path={}", specPath);
            return WorknetApiSpecFile.defaults();
        }
        try (InputStream in = resource.getInputStream()) {
            WorknetApiSpecFile file = objectMapper.readValue(in, WorknetApiSpecFile.class);
            log.info("[worknet] 스펙 파일 로드 path={}, regionMapping={}건, jobMapping={}건, "
                            + "regionPassthrough={}, jobPassthrough={}",
                    specPath,
                    file.mapping().regionCodes().size(),
                    file.mapping().jobCodes().size(),
                    file.mapping().regionCodePassthrough(),
                    file.mapping().jobCodePassthrough());
            return file;
        } catch (Exception e) {
            log.error("[worknet] 스펙 파일 파싱 실패 - 기본값으로 동작한다. path={}", specPath, e);
            return WorknetApiSpecFile.defaults();
        }
    }
}
