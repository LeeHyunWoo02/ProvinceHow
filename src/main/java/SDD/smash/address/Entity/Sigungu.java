package SDD.smash.Address.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Builder
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Sigungu {

    @Id
    @Column(name = "sigungu_code", length = 5)
    private String sigunguCode;

    @Column(name = "name", nullable = false)
    @NotNull
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sido_code", nullable = false)
    private Sido sido;
}
