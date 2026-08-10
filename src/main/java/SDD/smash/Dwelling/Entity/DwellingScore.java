package SDD.smash.Dwelling.Entity;

import SDD.smash.Address.Entity.Sigungu;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

@Entity
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class DwellingScore {
    @Id
    @Column(name = "sigungu_code", length = 5)
    private String sigunguCode;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "sigungu_code")
    private Sigungu sigungu;

    @Min(0)
    @Max(100)
    private Integer score;
}
