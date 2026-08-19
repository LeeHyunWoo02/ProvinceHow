package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.infrastructure.batch.dto.InfraTargetResult;
import SDD.smash.domain.infra.infrastructure.persistence.InfraCollectionStagingStore;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * 수집 결과를 staging 에 쓴다. <b>서비스 테이블({@code infra})은 건드리지 않는다.</b>
 *
 * <h2>왜 한 Writer 가 두 테이블을 쓰는가</h2>
 * "대상 진행"과 "그 대상이 만든 카운트"는 <b>같은 트랜잭션에서 커밋돼야</b> 한다.
 * 카운트만 커밋되고 진행 행이 없으면, 다음 실행이 그 대상을 미수집으로 보고 다시 받아
 * <b>이중 합산</b>이 된다. 청크 트랜잭션({@code dataTransactionManager}) 하나에 둘을 묶는 것이
 * 이 Writer 의 존재 이유다.
 *
 * <p>카운트는 <b>합산 upsert</b> 다. 일반구 재분배 때문에 대상 하나가 여러 시군구를 만들고,
 * 여러 대상이 같은 시군구로 모이기도 한다. 이중 합산은 "이미 진행 행이 있는 대상은 다시
 * 수집하지 않는다"는 Reader 쪽 규칙이 막는다.
 */
public class InfraStagingWriter implements ItemWriter<InfraTargetResult> {

    private final InfraCollectionStagingStore stagingStore;
    private final String runKey;

    public InfraStagingWriter(InfraCollectionStagingStore stagingStore, String runKey) {
        this.stagingStore = stagingStore;
        this.runKey = runKey;
    }

    @Override
    public void write(Chunk<? extends InfraTargetResult> chunk) {
        List<InfraCollectionStagingStore.StagedTarget> rows = new ArrayList<>(chunk.size());
        for (InfraTargetResult item : chunk) {
            rows.add(new InfraCollectionStagingStore.StagedTarget(
                    item.target().regionCodeValue(),
                    item.target().industryCodeValue(),
                    item.counts(),
                    item.facilityCount(),
                    item.apiCalls()));
        }
        stagingStore.save(runKey, rows);
    }
}
