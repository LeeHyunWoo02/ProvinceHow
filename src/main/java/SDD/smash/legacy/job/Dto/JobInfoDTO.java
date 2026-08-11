package SDD.smash.legacy.job.Dto;

import lombok.Getter;
import lombok.Setter;

@Getter
public class JobInfoDTO {

    private Long count;

    @Setter
    private String url;

    public JobInfoDTO(Long count)
    {
        this.count = (count == null) ? 0L : count;
    }

    public JobInfoDTO(Integer count)
    {
        this.count = (count == null) ? 0L : count.longValue();
    }

}
