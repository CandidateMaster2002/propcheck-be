package com.propchk.be.service;

import com.propchk.be.dto.LoginResponse;
import com.propchk.be.entity.User;
import com.propchk.be.exception.InvalidCredentialsException;
import com.propchk.be.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        boolean passwordMatches = false;
        if (user.getPassword().startsWith("$2a$")) {
            passwordMatches = org.mindrot.jbcrypt.BCrypt.checkpw(password, user.getPassword());
        } else {
            passwordMatches = user.getPassword().equals(password);
        }

        if (!passwordMatches) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCity()
        );
    }
    public void changePassword(String email, String oldPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        boolean passwordMatches = false;
        if (user.getPassword().startsWith("$2a$")) {
            passwordMatches = org.mindrot.jbcrypt.BCrypt.checkpw(oldPassword, user.getPassword());
        } else {
            passwordMatches = user.getPassword().equals(oldPassword);
        }

        if (!passwordMatches) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String hashedNew = org.mindrot.jbcrypt.BCrypt.hashpw(newPassword, org.mindrot.jbcrypt.BCrypt.gensalt());
        user.setPassword(hashedNew);
        userRepository.save(user);
    }
}
