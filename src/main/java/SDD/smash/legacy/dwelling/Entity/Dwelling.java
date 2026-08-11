package SDD.smash.legacy.dwelling.Entity;

import SDD.smash.legacy.address.Entity.Sigungu;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Dwelling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "sigungu_code",
            foreignKey = @ForeignKey(ConstraintMode.CONSTRAINT)
    )
    private Sigungu sigungu;

    @Column(name = "month_avg")
    private Double monthAvg;

    @Column(name = "month_mid")
    private Integer monthMid;

    @Column(name = "jeonse_avg")
    private Double jeonseAvg;
    @Column(name = "jeonse_mid")
    private Integer jeonseMid;

}
