package SDD.smash.domain.recommendation.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.domain.job.application.JobQueryService;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.dto.CodeItem;
import SDD.smash.domain.support.domain.model.SupportTag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 코드 목록 조회 유스케이스.
 *
 * <p>존재 검증(직종 대분류, 시도)은 각 컨텍스트의 application Service(값 객체 생성자 +
 * 조회 메서드)가 이미 수행하므로 여기서 다시 검사하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RegionCodeService {

    private final JobQueryService jobQueryService;
    private final AddressQueryService addressQueryService;

    public List<CodeItem> getAllJobTops() {
        return jobQueryService.getAllTopCategories().stream()
                .map(v -> new CodeItem(v.code().value(), v.name()))
                .toList();
    }

    public List<CodeItem> getAllJobMidsByTop(JobCode topCode) {
        return jobQueryService.getSubCategoriesOf(topCode).stream()
                .map(v -> new CodeItem(v.code().value(), v.name()))
                .toList();
    }

    public List<CodeItem> getAllSidos() {
        return addressQueryService.getAllSidos().stream()
                .map(v -> new CodeItem(v.code().value(), v.name()))
                .toList();
    }

    public List<CodeItem> getAllSigungusBySido(SidoCode sidoCode) {
        return addressQueryService.getSigungusBySido(sidoCode).stream()
                .map(v -> new CodeItem(v.code().value(), v.name()))
                .toList();
    }

    public List<CodeItem> getAllSupportTags() {
        return Arrays.stream(SupportTag.values())
                .map(tag -> new CodeItem(tag.name(), tag.getValue()))
                .toList();
    }
}
