package com.beertracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for the Beer Tracker Backend API service.
 */
@SpringBootApplication
public class BeerTrackerApplication {

    /**
     * Launches the Beer Tracker Spring Boot web server.
     *
     * @param args standard command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(BeerTrackerApplication.class, args);
    }
}
