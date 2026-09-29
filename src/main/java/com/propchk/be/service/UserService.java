package com.propchk.be.service;

import com.propchk.be.dto.CreateCityHeadRequest;
import com.propchk.be.dto.CreateSalesRequest;
import com.propchk.be.dto.UserSummary;
import com.propchk.be.entity.Role;
import com.propchk.be.entity.User;
import com.propchk.be.exception.DuplicateEmailException;
import com.propchk.be.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserSummary createSalesUser(CreateSalesRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new DuplicateEmailException("Email already exists");
        }

        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPassword(req.getPassword());
        user.setRole(Role.SALES);
        user.setCity(null);

        user = userRepository.save(user);
        return mapToSummary(user);
    }

    public UserSummary createCityHeadUser(CreateCityHeadRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new DuplicateEmailException("Email already exists");
        }

        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPassword(req.getPassword());
        user.setRole(Role.CITY_HEAD);
        user.setCity(req.getCity());

        user = userRepository.save(user);
        return mapToSummary(user);
    }

    public List<UserSummary> listUsers(String roleFilter) {
        List<User> users;
        if (roleFilter == null || roleFilter.trim().isEmpty()) {
            users = userRepository.findAll();
        } else {
            Role roleEnum;
            try {
                roleEnum = Role.valueOf(roleFilter.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return List.of();
            }
            users = userRepository.findByRole(roleEnum);
        }

        return users.stream().map(this::mapToSummary).collect(Collectors.toList());
    }

    private UserSummary mapToSummary(User user) {
        return new UserSummary(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCity()
        );
    }
}
