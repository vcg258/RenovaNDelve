package com.renovandelve.config;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@Log4j2
@SpringBootTest
class SecurityConfigTest {

    @Test
    void passwordEncoder() {
        BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
        log.info("BCryptPasswordEncoder: {}", bCryptPasswordEncoder.encode("2222"));
    }
}