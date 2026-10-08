package com.example.bullhorn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic application context test.
 * Verifies Spring Boot application starts successfully.
 */
@SpringBootTest
@ActiveProfiles("dev")
class BullhornTimesheetApplicationTests {

    @Test
    void contextLoads() {
        // Verifies application context loads without errors
    }
}
