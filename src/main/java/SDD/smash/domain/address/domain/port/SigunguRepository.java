package SDD.smash.domain.address.domain.port;

import SDD.smash.domain.address.domain.model.Sigungu;
import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.global.domain.model.SigunguCode;

import java.util.List;

/** 시군구 저장소 out-port. */
public interface SigunguRepository {

    List<Sigungu> findAll();

    /** 코드만 필요한 경로(전 시군구 순회)를 위한 조회. */
    List<SigunguCode> findAllCodes();

    List<Sigungu> findAllBy(SidoCode sidoCode);

    boolean existsBy(SigunguCode code);
}
