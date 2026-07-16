package com.renovandelve.service;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@Log4j2
@SpringBootTest
class MonsterServiceImplTest {
    @Autowired private MonsterService monsterService;

    @Test
    void getMonsterByNameNormal() {
        monsterService.getMonsterByNameNormal(List.of("슬라임", "해골 병사")).forEach(log::info);
    }

    @Test
    void getRandomMonsterNormal() {
        log.info(monsterService.getRandomMonsterNormal());
    }

    @Test
    void getMonsterByNameBoss() {
        log.info(monsterService.getMonsterByNameBoss(List.of("기사단장", "악마화된 왕")));
    }

    @Test
    void getRandomMonsterBoss() {
        log.info(monsterService.getRandomMonsterBoss());
    }
}