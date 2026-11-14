package com.renovandelve.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class MonsterRagServiceTest {
    @Autowired private MonsterRagService monsterRagService;

    @Test
    void chat() {
        monsterRagService.chat(1, 1, "1");
    }
}