package SDD.smash.legacy.job.Dto;

import lombok.Getter;

@Getter
public class JobCountDTO {
    String sigunguCode;
    Long totalCount;

    public JobCountDTO(String sigunguCode, Long totalCount)
    {
        this.sigunguCode = sigunguCode;
        this.totalCount = (totalCount == null) ? 0L : totalCount;

    }

    public JobCountDTO(String sigunguCode, Integer cnt)
    {
        this.sigunguCode = sigunguCode;
        this.totalCount = (cnt != null) ? cnt.longValue() : 0L;
    }
}
