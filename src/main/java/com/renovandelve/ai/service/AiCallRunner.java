package com.renovandelve.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 시간 상한을 둔 LLM 호출. 상한을 넘기거나 실패하면 빈 값을 돌려주고, 부르는 쪽이 정해진 폴백을 쓴다.
 * <ul>
 *   <li>LLM 호출은 대기 시간이 대부분인 블로킹 IO라 가상 스레드에서 돌린다</li>
 *   <li>스프링 빈이라 종료 시점에 실행기를 닫는다(진행 중 호출은 최대 10초 기다림)</li>
 *   <li>로그의 MDC를 호출 스레드에서 작업 스레드로 넘긴다</li>
 * </ul>
 * Boot의 {@code applicationTaskExecutor}(@Async, 메일, 프리페치)와 따로 둔 이유: 그 풀은 기본 8스레드라
 * 75초까지 걸리는 노드 생성이 그 줄에 서면 대기 시간까지 상한에 포함된다. 또한 {@code Executor} 빈을 하나 더 등록하면
 * Boot의 자동 구성 실행기가 물러나므로 빈이 아니라 이 컴포넌트 안에 둔다.
 */
@Slf4j
@Component
public class AiCallRunner implements DisposableBean {

    private static final long TERMINATION_TIMEOUT_MILLIS = 10_000;

    private final SimpleAsyncTaskExecutor executor;

    /** 가상 스레드 실행기를 만든다 */
    public AiCallRunner() {
        executor = new SimpleAsyncTaskExecutor("ai-call-");
        executor.setVirtualThreads(true);
        executor.setTaskTerminationTimeout(TERMINATION_TIMEOUT_MILLIS);
        executor.setTaskDecorator(task -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) MDC.setContextMap(context);
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });
    }

    /**
     * 작업을 시간 상한 안에서 실행한다. 상한을 넘기면 작업 스레드를 인터럽트한다.
     * 인터럽트로 이미 나간 HTTP 요청이 바로 끊긴다는 보장은 없다.
     *
     * @param task           LLM 호출
     * @param timeoutSeconds 시간 상한(초)
     * @param label          로그에 남길 작업 이름
     * @param <T>            결과 타입
     * @return 결과, 시간 초과·실패·null이면 빈 값
     */
    public <T> Optional<T> call(Callable<T> task, long timeoutSeconds, String label) {
        Future<T> pending = executor.submit(task);
        try {
            return Optional.ofNullable(pending.get(timeoutSeconds, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            pending.cancel(true);
            log.warn("[AI 호출] {} {}초 초과, 폴백 사용", label, timeoutSeconds);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("[AI 호출] {} 대기 중 인터럽트, 폴백 사용", label);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("[AI 호출] {} 실패, 폴백 사용 원인={}", label, cause.getMessage());
        }
        return Optional.empty();
    }

    /** 종료 시 실행기를 닫는다 */
    @Override
    public void destroy() {
        executor.close();
    }
}
