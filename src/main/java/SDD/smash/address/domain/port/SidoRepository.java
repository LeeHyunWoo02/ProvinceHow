package SDD.smash.address.domain.port;

import SDD.smash.address.domain.model.Sido;
import SDD.smash.common.domain.model.SidoCode;

import java.util.List;

/** 시도 저장소 out-port. */
public interface SidoRepository {

    List<Sido> findAll();

    boolean existsBy(SidoCode code);
}
