package SDD.smash.legacy.infra.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Industry {
    @Id
    @Column(name = "industry_code", length = 10, nullable = false)
    private String code;

    @NotNull
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Major major;
}
