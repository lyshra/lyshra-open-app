package com.lyshra.open.app.integration.contract.version.migration;

import java.util.List;
import java.util.Optional;

/**
 * Result of migration validation (pre-flight check).
 * Determines if migration can safely proceed.
 */
public interface IMigrationValidationResult {

    /**
     * Validation outcome.
     */
    enum Outcome {
        /**
         * Migration can proceed safely.
         */
        VALID,

        /**
         * Migration can proceed with warnings.
         */
        VALID_WITH_WARNINGS,

        /**
         * Migration should not proceed - blocking issues found.
         */
        INVALID,

        /**
         * Cannot determine validity - manual review required.
         */
        REQUIRES_REVIEW
    }

    /**
     * Returns the validation outcome.
     *
     * @return outcome
     */
    Outcome getOutcome();

    /**
     * Returns the list of validation errors (blocking issues).
     *
     * @return error list
     */
    List<String> getErrors();

    /**
     * Returns the list of validation warnings (non-blocking issues).
     *
     * @return warning list
     */
    List<String> getWarnings();

    /**
     * Returns the list of informational messages.
     *
     * @return info list
     */
    List<String> getInfo();

    /**
     * Returns the recommended target step after migration.
     *
     * @return recommended step name
     */
    Optional<String> getRecommendedTargetStep();

    /**
     * Indicates if the current step is a safe migration point.
     *
     * @return true if safe
     */
    boolean isAtSafeMigrationPoint();

    /**
     * Returns estimated data loss risk level (0.0 = none, 1.0 = total).
     *
     * @return risk level
     */
    double getDataLossRisk();

    /**
     * Checks if validation passed (VALID or VALID_WITH_WARNINGS).
     *
     * @return true if passed
     */
    default boolean isValid() {
        Outcome o = getOutcome();
        return o == Outcome.VALID || o == Outcome.VALID_WITH_WARNINGS;
    }
}
