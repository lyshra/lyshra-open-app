package com.lyshra.open.app.designer.controller;

import com.lyshra.open.app.designer.domain.User;
import com.lyshra.open.app.designer.domain.UserRole;
import com.lyshra.open.app.designer.dto.UserCreateRequest;
import com.lyshra.open.app.designer.dto.UserUpdateRequest;
import com.lyshra.open.app.designer.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for user management operations.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<User>> createUser(@Valid @RequestBody UserCreateRequest request) {
        return userService.createUser(request)
                .map(user -> ResponseEntity.status(HttpStatus.CREATED).body(user));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<User> getAllUsers(@RequestParam(required = false) UserRole role) {
        if (role != null) {
            return userService.getUsersByRole(role);
        }
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<User>> getUser(@PathVariable String id) {
        return userService.getUser(id)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<User>> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateUser(id, request)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable String id) {
        return userService.deleteUser(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<User>> enableUser(@PathVariable String id) {
        return userService.enableUser(id)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<User>> disableUser(@PathVariable String id) {
        return userService.disableUser(id)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{id}/password")
    public Mono<ResponseEntity<Void>> changePassword(
            @PathVariable String id,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {
        return userService.changePassword(id, oldPassword, newPassword)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PutMapping("/{id}/password/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> resetPassword(
            @PathVariable String id,
            @RequestParam String newPassword) {
        return userService.resetPassword(id, newPassword)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PutMapping("/{id}/roles/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<User>> addRole(
            @PathVariable String id,
            @PathVariable UserRole role) {
        return userService.addRole(id, role)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}/roles/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<User>> removeRole(
            @PathVariable String id,
            @PathVariable UserRole role) {
        return userService.removeRole(id, role)
                .map(ResponseEntity::ok);
    }
}
