package SDD.smash.domain.address.domain.port;

import SDD.smash.domain.address.domain.model.Sido;
import SDD.smash.global.domain.model.SidoCode;

import java.util.List;

/** 시도 저장소 out-port. */
public interface SidoRepository {

    List<Sido> findAll();

    boolean existsBy(SidoCode code);
}
