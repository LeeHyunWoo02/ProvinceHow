package SDD.smash.legacy.infra.Repository;

import SDD.smash.legacy.infra.Entity.Industry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndustryRepository extends JpaRepository<Industry, String> {
}
