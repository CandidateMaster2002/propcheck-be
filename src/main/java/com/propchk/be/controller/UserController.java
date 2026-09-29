package com.propchk.be.controller;

import com.propchk.be.dto.CreateCityHeadRequest;
import com.propchk.be.dto.CreateSalesRequest;
import com.propchk.be.dto.UserSummary;
import com.propchk.be.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/sales")
    public ResponseEntity<UserSummary> createSalesUser(@Valid @RequestBody CreateSalesRequest request) {
        UserSummary summary = userService.createSalesUser(request);
        return new ResponseEntity<>(summary, HttpStatus.CREATED);
    }

    @PostMapping("/cityhead")
    public ResponseEntity<UserSummary> createCityHeadUser(@Valid @RequestBody CreateCityHeadRequest request) {
        UserSummary summary = userService.createCityHeadUser(request);
        return new ResponseEntity<>(summary, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<UserSummary>> listUsers(@RequestParam(required = false, name = "role") String role) {
        List<UserSummary> users = userService.listUsers(role);
        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<java.util.Map<String, String>> deleteUser(@PathVariable Long id) {
        // TODO: Ensure this is called by an ADMIN role (Frontend handles routing, but backend should ideally verify token)
        userService.deleteUser(id);
        return ResponseEntity.ok(java.util.Map.of("message", "User deleted successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<java.util.Map<String, String>> updateUser(@PathVariable Long id, @RequestBody com.propchk.be.dto.UpdateUserRequest request) {
        // TODO: Ensure this is called by an ADMIN role
        userService.updateUser(id, request);
        return ResponseEntity.ok(java.util.Map.of("message", "User updated successfully"));
    }
}
