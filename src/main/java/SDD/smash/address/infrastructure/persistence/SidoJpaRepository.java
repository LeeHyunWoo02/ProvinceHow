package SDD.smash.address.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SidoJpaRepository extends JpaRepository<SidoJpaEntity, String> {

    boolean existsBySidoCode(String sidoCode);
}
