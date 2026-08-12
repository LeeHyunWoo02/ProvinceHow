package SDD.smash.domain.recommendation.application;

import SDD.smash.domain.address.application.port.in.AddressQueryUseCase;
import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.domain.job.application.port.in.JobQueryUseCase;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.dto.CodeItem;
import SDD.smash.domain.recommendation.application.port.in.RegionCodeUseCase;
import SDD.smash.domain.support.domain.model.SupportTag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 코드 목록 조회 유스케이스. As-Is {@code CodeService} 를 옮긴 것이다.
 *
 * <p>존재 검증(직종 대분류, 시도)은 각 컨텍스트의 in-port(값 객체 생성자 + 조회 메서드)가
 * 이미 수행하므로 여기서 다시 검사하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RegionCodeService implements RegionCodeUseCase {

    private final JobQueryUseCase jobQueryUseCase;
    private final AddressQueryUseCase addressQueryUseCase;

    @Override
    public List<CodeItem> getAllJobTops() {
        return jobQueryUseCase.getAllTopCategories().stream()
                .map(v -> new CodeItem(v.code().value(), v.name()))
                .toList();
    }

    @Override
    public List<CodeItem> getAllJobMidsByTop(JobCode topCode) {
        return jobQueryUseCase.getSubCategoriesOf(topCode).stream()
                .map(v -> new CodeItem(v.code().value(), v.name()))
                .toList();
    }

    @Override
    public List<CodeItem> getAllSidos() {
        return addressQueryUseCase.getAllSidos().stream()
                .map(v -> new CodeItem(v.code().value(), v.name()))
                .toList();
    }

    @Override
    public List<CodeItem> getAllSigungusBySido(SidoCode sidoCode) {
        return addressQueryUseCase.getSigungusBySido(sidoCode).stream()
                .map(v -> new CodeItem(v.code().value(), v.name()))
                .toList();
    }

    @Override
    public List<CodeItem> getAllSupportTags() {
        return Arrays.stream(SupportTag.values())
                .map(tag -> new CodeItem(tag.name(), tag.getValue()))
                .toList();
    }
}
