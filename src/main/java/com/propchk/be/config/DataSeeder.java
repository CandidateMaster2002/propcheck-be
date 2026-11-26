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
        if (!userRepository.existsByRole(Role.SUPER_ADMIN)) {
            User superAdmin = userRepository.findByEmail("admin@example.com").orElse(new User());
            if (superAdmin.getId() == null) {
                superAdmin.setName("Super Admin");
                superAdmin.setEmail("admin@example.com");
                superAdmin.setPassword("123");
                superAdmin.setCity(null);
            }
            superAdmin.setRole(Role.SUPER_ADMIN);
            superAdmin.setCanValidate(true);
            userRepository.save(superAdmin);
            logger.info("Super Admin user ensured in the database (admin@example.com).");
        } else {
            logger.info("Super Admin user already exists. Seeding skipped.");
        }
    }
}
