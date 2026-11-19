package com.renovandelve.ai.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallRunnerTest {

    private final AiCallRunner runner = new AiCallRunner();

    @AfterEach
    void close() {
        runner.destroy();
    }

    @Test
    @DisplayName("상한 안에 끝나면 결과를 돌려준다")
    void returnsResult() {
        assertThat(runner.call(() -> "장면", 5, "테스트")).contains("장면");
    }

    @Test
    @DisplayName("실패하거나 null이면 빈 값 (부르는 쪽이 폴백)")
    void emptyOnFailure() {
        assertThat(runner.call(() -> { throw new IllegalStateException("LLM 오류"); }, 5, "테스트")).isEmpty();
        assertThat(runner.call(() -> null, 5, "테스트")).isEmpty();
    }

    @Test
    @DisplayName("상한을 넘기면 빈 값을 돌려주고 작업 스레드를 인터럽트한다")
    void timeoutInterrupts() throws InterruptedException {
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean(false);

        var result = runner.call(() -> {
            try {
                Thread.sleep(10_000);
                finished.set(true);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return "늦은 장면";
        }, 1, "테스트");

        assertThat(result).isEmpty();
        assertThat(interrupted.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(finished).isFalse();
    }
}
