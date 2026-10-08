package com.example.bullhorn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BullhornTimesheetApplication {

    public static void main(String[] args) {
        SpringApplication.run(BullhornTimesheetApplication.class, args);
    }
}
