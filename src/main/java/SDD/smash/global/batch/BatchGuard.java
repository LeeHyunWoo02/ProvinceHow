package SDD.smash.global.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BatchGuard {
    private final JobExplorer jobExplorer;

    /**
     * 같은 jobName과 seedVersion 파라미터로 completed 가 1개라도 있으면 true
     * */
    public boolean alreadyDone(String jobName, String seedVersion) {
        List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, 20);
        for (JobInstance instance : instances) {
            List<JobExecution> execs = jobExplorer.getJobExecutions(instance);
            for (JobExecution exec : execs) {
                JobParameters param = exec.getJobParameters();
                String v = param.getString("seedVersion"); // 고정 파라미터 키
                if (seedVersion.equals(v) && exec.getStatus() == BatchStatus.COMPLETED) {
                    return true;
                }
            }
        }
        return false;
    }
}
