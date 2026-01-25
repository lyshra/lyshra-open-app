package com.lyshra.open.app.designer.repository;

import com.lyshra.open.app.designer.domain.User;
import com.lyshra.open.app.designer.domain.UserRole;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Repository interface for users.
 */
public interface UserRepository {

    /**
     * Saves a user.
     *
     * @param user the user to save
     * @return Mono containing the saved user
     */
    Mono<User> save(User user);

    /**
     * Finds a user by ID.
     *
     * @param id the user ID
     * @return Mono containing the user if found
     */
    Mono<Optional<User>> findById(String id);

    /**
     * Finds a user by username.
     *
     * @param username the username
     * @return Mono containing the user if found
     */
    Mono<Optional<User>> findByUsername(String username);

    /**
     * Finds a user by email.
     *
     * @param email the email
     * @return Mono containing the user if found
     */
    Mono<Optional<User>> findByEmail(String email);

    /**
     * Finds all users.
     *
     * @return Flux of all users
     */
    Flux<User> findAll();

    /**
     * Finds users by role.
     *
     * @param role the role
     * @return Flux of users with the role
     */
    Flux<User> findByRole(UserRole role);

    /**
     * Finds enabled users.
     *
     * @return Flux of enabled users
     */
    Flux<User> findEnabled();

    /**
     * Deletes a user by ID.
     *
     * @param id the user ID
     * @return Mono completing when delete is done
     */
    Mono<Void> deleteById(String id);

    /**
     * Checks if a username exists.
     *
     * @param username the username
     * @return Mono containing true if exists
     */
    Mono<Boolean> existsByUsername(String username);

    /**
     * Checks if an email exists.
     *
     * @param email the email
     * @return Mono containing true if exists
     */
    Mono<Boolean> existsByEmail(String email);
}
