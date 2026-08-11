package SDD.smash.Apis.Service;

import SDD.smash.Address.Repository.SidoRepository;
import SDD.smash.Address.Repository.SigunguRepository;
import SDD.smash.Apis.Dto.CodeDTO;
import SDD.smash.Exception.Code.ErrorCode;
import SDD.smash.Exception.Exception.BusinessException;
import SDD.smash.Job.Repository.JobCodeMiddleRepository;
import SDD.smash.Job.Repository.JobCodeTopRepository;
import SDD.smash.Support.domain.SupportTag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CodeService {

    private final SidoRepository sidoRepository;
    private final JobCodeTopRepository jobCodeTopRepository;
    private final JobCodeMiddleRepository jobCodeMiddleRepository;
    private final SigunguRepository sigunguRepository;

    @Transactional(readOnly = true)
    public List<CodeDTO> getAllJobTops()
    {
        return jobCodeTopRepository.getCodeDTOList();
    }

    @Transactional(readOnly = true)
    public List<CodeDTO> getAllJobMidsByTop(String topCode)
    {
        //topCode 검증
        if(topCode==null || !jobCodeTopRepository.existsByCode(topCode))
            throw new BusinessException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종코드");

        return jobCodeMiddleRepository.getCodeDTOListByTopCode(topCode);

    }

    @Transactional(readOnly = true)
    public List<CodeDTO> getAllSidos()
    {
        return sidoRepository.getCodeDTOList();

    }

    @Transactional(readOnly = true)
    public List<CodeDTO> getAllSigungusBySido(String sidoCode)
    {
        if(sidoCode == null || !sidoRepository.existsBySidoCode(sidoCode))
            throw new BusinessException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 지역코드");

        return sigunguRepository.getCodeDTOListBySidoCode(sidoCode);
    }

    @Transactional(readOnly = true)
    public List<CodeDTO> getAllSupportTags()
    {
        return Arrays.stream(SupportTag.values())
                .map(tag -> new CodeDTO(tag.name(), tag.getValue()))
                .collect(Collectors.toList());
    }
}
