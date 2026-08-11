package SDD.smash.legacy.job.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor @AllArgsConstructor
public class JobCodeTop {

    @Id
    private String code;

    @NotNull
    private String name;

}
