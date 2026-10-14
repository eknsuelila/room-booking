package com.portfolio.roombooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Room Booking REST API.
 *
 * <p>{@code @SpringBootApplication} is a shortcut for three annotations:
 * <ul>
 *   <li>{@code @Configuration} - this class may declare beans;</li>
 *   <li>{@code @EnableAutoConfiguration} - Spring Boot configures Tomcat, Jackson, JPA, Security,
 *       Flyway... automatically, based on the libraries found in pom.xml;</li>
 *   <li>{@code @ComponentScan} - every {@code @Component/@Service/@RestController/@Repository} in this
 *       package and its sub-packages is discovered and created ("component scanning").</li>
 * </ul>
 * That is why the main class lives in the ROOT package {@code com.portfolio.roombooking}: everything
 * else sits below it and is picked up automatically.
 *
 * <p>Package layout (by feature, not by layer, so related code stays together):
 * <pre>
 *  auth/      register + login endpoints
 *  user/      User entity, Role enum, repository
 *  room/      Room entity, CRUD + availability + schedule
 *  booking/   Booking entity, business rules, overlap detection
 *  stats/     admin dashboard numbers
 *  security/  JWT creation/validation and the request filter
 *  config/    Spring Security rules, Clock, OpenAPI, demo data
 *  common/    error handling and shared DTOs
 * </pre>
 */
@SpringBootApplication
public class RoomBookingApplication {

    /** Starts the embedded web server (Tomcat) and the whole Spring application context. */
    public static void main(String[] args) {
        SpringApplication.run(RoomBookingApplication.class, args);
    }
}
