package com.lyshra.open.app.integration.models.version.migration;

import com.lyshra.open.app.integration.contract.version.migration.IMigrationValidationResult;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of migration validation result.
 */
@Data
@Builder
public class MigrationValidationResult implements IMigrationValidationResult, Serializable {

    private static final long serialVersionUID = 1L;

    private final Outcome outcome;
    @Builder.Default
    private final List<String> errors = new ArrayList<>();
    @Builder.Default
    private final List<String> warnings = new ArrayList<>();
    @Builder.Default
    private final List<String> info = new ArrayList<>();
    private final String recommendedTargetStep;
    private final boolean atSafeMigrationPoint;
    @Builder.Default
    private final double dataLossRisk = 0.0;

    @Override
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    @Override
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    @Override
    public List<String> getInfo() {
        return Collections.unmodifiableList(info);
    }

    @Override
    public Optional<String> getRecommendedTargetStep() {
        return Optional.ofNullable(recommendedTargetStep);
    }

    /**
     * Creates a valid result.
     */
    public static MigrationValidationResult valid(String targetStep, boolean atSafePoint) {
        return MigrationValidationResult.builder()
                .outcome(Outcome.VALID)
                .recommendedTargetStep(targetStep)
                .atSafeMigrationPoint(atSafePoint)
                .build();
    }

    /**
     * Creates a valid result with warnings.
     */
    public static MigrationValidationResult validWithWarnings(String targetStep, boolean atSafePoint, List<String> warnings) {
        return MigrationValidationResult.builder()
                .outcome(Outcome.VALID_WITH_WARNINGS)
                .recommendedTargetStep(targetStep)
                .atSafeMigrationPoint(atSafePoint)
                .warnings(new ArrayList<>(warnings))
                .build();
    }

    /**
     * Creates an invalid result.
     */
    public static MigrationValidationResult invalid(List<String> errors) {
        return MigrationValidationResult.builder()
                .outcome(Outcome.INVALID)
                .errors(new ArrayList<>(errors))
                .atSafeMigrationPoint(false)
                .dataLossRisk(1.0)
                .build();
    }

    /**
     * Creates a requires review result.
     */
    public static MigrationValidationResult requiresReview(List<String> warnings, double riskLevel) {
        return MigrationValidationResult.builder()
                .outcome(Outcome.REQUIRES_REVIEW)
                .warnings(new ArrayList<>(warnings))
                .atSafeMigrationPoint(false)
                .dataLossRisk(riskLevel)
                .build();
    }

    /**
     * Builder extension for fluent API.
     */
    public static class MigrationValidationResultBuilder {

        public MigrationValidationResultBuilder addError(String error) {
            if (this.errors$value == null) {
                this.errors$value = new ArrayList<>();
                this.errors$set = true;
            }
            this.errors$value.add(error);
            return this;
        }

        public MigrationValidationResultBuilder addWarning(String warning) {
            if (this.warnings$value == null) {
                this.warnings$value = new ArrayList<>();
                this.warnings$set = true;
            }
            this.warnings$value.add(warning);
            return this;
        }

        public MigrationValidationResultBuilder addInfo(String infoMsg) {
            if (this.info$value == null) {
                this.info$value = new ArrayList<>();
                this.info$set = true;
            }
            this.info$value.add(infoMsg);
            return this;
        }
    }
}
