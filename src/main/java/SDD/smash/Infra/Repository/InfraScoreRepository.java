package SDD.smash.Infra.Repository;

import SDD.smash.Infra.Entity.InfraScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InfraScoreRepository extends JpaRepository<InfraScore,String> {
    List<InfraScore> findAllByOrderByScoreDesc();
}
