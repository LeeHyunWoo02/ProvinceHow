package SDD.smash.legacy.job.Entity;

import SDD.smash.legacy.address.Entity.Sigungu;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(
        name="JobCount",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"sigungu_code", "job_code_middle_code"})
        }
)
@Getter
public class JobCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sigungu_code", nullable = false)
    Sigungu sigungu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_code_middle_code", nullable = false)
    JobCodeMiddle jobCodeMiddle;

    @Column(nullable = false)
    Integer count;
}
