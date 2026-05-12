package com.flodiback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.flodiback.support.AbstractPostgresIntegrationTest;

@ActiveProfiles("test")
@SpringBootTest
class FlodiBackApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {}
}
