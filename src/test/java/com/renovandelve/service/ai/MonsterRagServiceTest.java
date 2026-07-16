package com.renovandelve.service.ai;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Log4j2
@SpringBootTest
class MonsterRagServiceTest {
    @Autowired private MonsterRagService monsterRagService;

    @Test
    void chat() {
        monsterRagService.chat(1, 1, "1");
    }
}