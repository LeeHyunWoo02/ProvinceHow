package SDD.smash.domain.recommendation.application.port.in;

import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.dto.CodeItem;

import java.util.List;

/**
 * 코드 목록 조회 in-port. As-Is {@code CodeService} 자리다.
 *
 * <p>각 메서드는 각 컨텍스트의 in-port로 위임할 뿐 자체 저장소를 갖지 않는다.
 */
public interface RegionCodeUseCase {

    List<CodeItem> getAllJobTops();

    /** 해당 대분류가 없으면 {@code JOB_CODE_NOT_FOUND} 를 던진다. */
    List<CodeItem> getAllJobMidsByTop(JobCode topCode);

    List<CodeItem> getAllSidos();

    /** 해당 시도가 없으면 {@code ADDRESS_CODE_NOT_FOUND} 를 던진다. */
    List<CodeItem> getAllSigungusBySido(SidoCode sidoCode);

    /** RDB 조회가 아니라 {@code SupportTag} 정적 열거다. */
    List<CodeItem> getAllSupportTags();
}
