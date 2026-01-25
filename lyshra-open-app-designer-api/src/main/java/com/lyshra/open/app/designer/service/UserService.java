package com.lyshra.open.app.designer.service;

import com.lyshra.open.app.designer.domain.User;
import com.lyshra.open.app.designer.domain.UserRole;
import com.lyshra.open.app.designer.dto.UserCreateRequest;
import com.lyshra.open.app.designer.dto.UserUpdateRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service interface for user management operations.
 */
public interface UserService {

    /**
     * Creates a new user.
     *
     * @param request the user creation request
     * @return Mono containing the created user
     */
    Mono<User> createUser(UserCreateRequest request);

    /**
     * Updates an existing user.
     *
     * @param id the user ID
     * @param request the update request
     * @return Mono containing the updated user
     */
    Mono<User> updateUser(String id, UserUpdateRequest request);

    /**
     * Gets a user by ID.
     *
     * @param id the user ID
     * @return Mono containing the user
     */
    Mono<User> getUser(String id);

    /**
     * Gets a user by username.
     *
     * @param username the username
     * @return Mono containing the user
     */
    Mono<User> getUserByUsername(String username);

    /**
     * Gets all users.
     *
     * @return Flux of all users
     */
    Flux<User> getAllUsers();

    /**
     * Gets users by role.
     *
     * @param role the role
     * @return Flux of users with the role
     */
    Flux<User> getUsersByRole(UserRole role);

    /**
     * Deletes a user.
     *
     * @param id the user ID
     * @return Mono completing when delete is done
     */
    Mono<Void> deleteUser(String id);

    /**
     * Enables a user account.
     *
     * @param id the user ID
     * @return Mono containing the updated user
     */
    Mono<User> enableUser(String id);

    /**
     * Disables a user account.
     *
     * @param id the user ID
     * @return Mono containing the updated user
     */
    Mono<User> disableUser(String id);

    /**
     * Changes a user's password.
     *
     * @param id the user ID
     * @param oldPassword the old password
     * @param newPassword the new password
     * @return Mono completing when password is changed
     */
    Mono<Void> changePassword(String id, String oldPassword, String newPassword);

    /**
     * Resets a user's password.
     *
     * @param id the user ID
     * @param newPassword the new password
     * @return Mono completing when password is reset
     */
    Mono<Void> resetPassword(String id, String newPassword);

    /**
     * Adds a role to a user.
     *
     * @param id the user ID
     * @param role the role to add
     * @return Mono containing the updated user
     */
    Mono<User> addRole(String id, UserRole role);

    /**
     * Removes a role from a user.
     *
     * @param id the user ID
     * @param role the role to remove
     * @return Mono containing the updated user
     */
    Mono<User> removeRole(String id, UserRole role);
}
