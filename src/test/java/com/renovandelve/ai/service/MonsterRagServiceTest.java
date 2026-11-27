package com.renovandelve.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
// 실제 OpenAI를 호출한다(비용 발생). GitHub Actions(CI=true)에서는 건너뛴다
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class MonsterRagServiceTest {
    @Autowired private MonsterRagService monsterRagService;

    @Test
    void chat() {
        monsterRagService.chat(1, 1, "1");
    }
}