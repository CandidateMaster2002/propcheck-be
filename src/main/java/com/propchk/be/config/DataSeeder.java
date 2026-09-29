package com.propchk.be.config;

import com.propchk.be.entity.Role;
import com.propchk.be.entity.User;
import com.propchk.be.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);
    private final UserRepository userRepository;

    public DataSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByRole(Role.ADMIN)) {
            User admin = new User();
            admin.setName("Admin");
            admin.setEmail("admin@example.com");
            admin.setPassword("123");
            admin.setRole(Role.ADMIN);
            admin.setCity(null);

            userRepository.save(admin);
            logger.info("Admin user was seeded into the database.");
        } else {
            logger.info("Admin user already exists. Seeding skipped.");
        }
    }
}
