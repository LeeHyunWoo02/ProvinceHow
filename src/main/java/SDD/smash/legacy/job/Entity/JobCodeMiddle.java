    package SDD.smash.legacy.job.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Builder
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class JobCodeMiddle {

    @Id
    private String code;

    @NotNull
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "top_code", nullable = false)
    private JobCodeTop jobCodeTop;

}
