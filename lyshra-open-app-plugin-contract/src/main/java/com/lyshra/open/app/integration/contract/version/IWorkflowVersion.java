package com.lyshra.open.app.integration.contract.version;

import java.time.Instant;
import java.util.Optional;

/**
 * Represents semantic versioning metadata for workflow definitions.
 * Follows SemVer 2.0.0 specification with extensions for workflow lifecycle.
 *
 * <p>Version format: MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]</p>
 *
 * <ul>
 *   <li>MAJOR: Incompatible changes that require explicit migration</li>
 *   <li>MINOR: Backward-compatible feature additions</li>
 *   <li>PATCH: Backward-compatible bug fixes</li>
 * </ul>
 */
public interface IWorkflowVersion extends Comparable<IWorkflowVersion> {

    /**
     * Returns the major version component.
     * Incremented for incompatible changes.
     *
     * @return major version number
     */
    int getMajor();

    /**
     * Returns the minor version component.
     * Incremented for backward-compatible features.
     *
     * @return minor version number
     */
    int getMinor();

    /**
     * Returns the patch version component.
     * Incremented for backward-compatible fixes.
     *
     * @return patch version number
     */
    int getPatch();

    /**
     * Returns optional pre-release identifier (e.g., "alpha", "beta", "rc.1").
     *
     * @return pre-release identifier if present
     */
    Optional<String> getPreRelease();

    /**
     * Returns optional build metadata (e.g., "build.123", "20240115").
     *
     * @return build metadata if present
     */
    Optional<String> getBuildMetadata();

    /**
     * Returns the timestamp when this version was created.
     *
     * @return creation timestamp
     */
    Instant getCreatedAt();

    /**
     * Indicates whether this version is deprecated.
     * Deprecated versions should not be used for new executions.
     *
     * @return true if deprecated
     */
    boolean isDeprecated();

    /**
     * Returns the deprecation reason if deprecated.
     *
     * @return deprecation reason if deprecated
     */
    Optional<String> getDeprecationReason();

    /**
     * Returns the full version string representation.
     *
     * @return version string (e.g., "1.2.3-beta+build.456")
     */
    String toVersionString();

    /**
     * Checks if this version is a pre-release version.
     *
     * @return true if pre-release
     */
    default boolean isPreRelease() {
        return getPreRelease().isPresent();
    }

    /**
     * Checks if this version is stable (not pre-release).
     *
     * @return true if stable
     */
    default boolean isStable() {
        return !isPreRelease();
    }
}
