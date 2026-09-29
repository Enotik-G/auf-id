package com.example.planner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "auth.password.pepper=test-pepper-only-for-tests-0123456789")
@Import(TestcontainersConfiguration.class)
class PlannerApplicationTests {

    @Test
    void contextLoads() {
    }

}
