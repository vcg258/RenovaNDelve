package com.renovandelve.repository;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
class HelmetRepositoryTest {
    @Autowired private HelmetRepository helmetRepository;

    @Test
    void findByNameContains() {
        log.info("{}", helmetRepository.findByNameContains("악마"));
    }
}