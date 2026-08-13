package SDD.smash.global.batch;

import org.springframework.batch.core.step.AbstractStep;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * {@link SeedStepExecutionListener} 를 <b>모든 Step 빈</b>에 붙인다.
 *
 * <p>Spring Batch 에는 "전 Step 공통 리스너" 훅이 없어서 보통 {@code StepBuilder} 마다
 * {@code .listener(...)} 를 적어야 한다. 그러면 배치 Config 9개가 같은 이유로 함께 바뀌고,
 * 새 배치를 추가할 때 빠뜨리기 쉽다. 등록을 한 곳으로 모으기 위해 BeanPostProcessor 를 쓴다.
 *
 * <p>리스너를 주입받지 않고 직접 생성하는 이유는 BeanPostProcessor 가 컨테이너 아주 이른 시점에
 * 만들어지기 때문이다. 다른 빈을 주입받으면 그 빈이 후처리 대상에서 빠지는 경고가 난다.
 * 이 리스너는 로깅 외에 의존이 없어 직접 생성해도 문제가 없다.
 */
@Component
public class SeedStepListenerRegistrar implements BeanPostProcessor {

    private final SeedStepExecutionListener listener = new SeedStepExecutionListener();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof AbstractStep step) {
            step.registerStepExecutionListener(listener);
        }
        return bean;
    }
}
