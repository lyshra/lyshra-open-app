package com.lyshra.open.app.integration.models.version;

import com.lyshra.open.app.integration.contract.version.IVersionCompatibility;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of version compatibility rules.
 * Defines how different workflow versions can interact and migrate.
 */
@Data
@Builder
public class VersionCompatibility implements IVersionCompatibility, Serializable {

    private static final long serialVersionUID = 1L;

    private final CompatibilityLevel level;
    private final IWorkflowVersion minimumCompatibleVersion;
    @Builder.Default
    private final Set<IWorkflowVersion> explicitlyCompatibleVersions = new HashSet<>();
    @Builder.Default
    private final Set<IWorkflowVersion> incompatibleVersions = new HashSet<>();
    private final boolean requiresContextMigration;
    private final boolean requiresVariableMigration;

    @Override
    public Set<IWorkflowVersion> getExplicitlyCompatibleVersions() {
        return Collections.unmodifiableSet(explicitlyCompatibleVersions);
    }

    @Override
    public Set<IWorkflowVersion> getIncompatibleVersions() {
        return Collections.unmodifiableSet(incompatibleVersions);
    }

    @Override
    public boolean requiresContextMigration() {
        return requiresContextMigration;
    }

    @Override
    public boolean requiresVariableMigration() {
        return requiresVariableMigration;
    }

    @Override
    public boolean isCompatibleWith(IWorkflowVersion fromVersion) {
        // Check explicit incompatibility
        if (incompatibleVersions.contains(fromVersion)) {
            return false;
        }

        // Check explicit compatibility
        if (!explicitlyCompatibleVersions.isEmpty()) {
            return explicitlyCompatibleVersions.contains(fromVersion);
        }

        // Range-based compatibility using minimum version
        if (minimumCompatibleVersion != null) {
            return fromVersion.compareTo(minimumCompatibleVersion) >= 0;
        }

        // Default based on compatibility level
        return level == CompatibilityLevel.FULL || level == CompatibilityLevel.BACKWARD;
    }

    /**
     * Creates full compatibility (no restrictions).
     *
     * @return full compatibility instance
     */
    public static VersionCompatibility full() {
        return VersionCompatibility.builder()
                .level(CompatibilityLevel.FULL)
                .requiresContextMigration(false)
                .requiresVariableMigration(false)
                .build();
    }

    /**
     * Creates backward compatibility from minimum version.
     *
     * @param minimumVersion minimum compatible version
     * @return backward compatibility instance
     */
    public static VersionCompatibility backwardFrom(IWorkflowVersion minimumVersion) {
        return VersionCompatibility.builder()
                .level(CompatibilityLevel.BACKWARD)
                .minimumCompatibleVersion(minimumVersion)
                .requiresContextMigration(false)
                .requiresVariableMigration(false)
                .build();
    }

    /**
     * Creates breaking change compatibility.
     *
     * @param requiresContextMigration whether context needs migration
     * @param requiresVariableMigration whether variables need migration
     * @return breaking compatibility instance
     */
    public static VersionCompatibility breaking(boolean requiresContextMigration, boolean requiresVariableMigration) {
        return VersionCompatibility.builder()
                .level(CompatibilityLevel.NONE)
                .requiresContextMigration(requiresContextMigration)
                .requiresVariableMigration(requiresVariableMigration)
                .build();
    }

    /**
     * Builder extension for fluent API.
     */
    public static class VersionCompatibilityBuilder {

        public VersionCompatibilityBuilder addCompatibleVersion(IWorkflowVersion version) {
            if (this.explicitlyCompatibleVersions$value == null) {
                this.explicitlyCompatibleVersions$value = new HashSet<>();
                this.explicitlyCompatibleVersions$set = true;
            }
            this.explicitlyCompatibleVersions$value.add(version);
            return this;
        }

        public VersionCompatibilityBuilder addIncompatibleVersion(IWorkflowVersion version) {
            if (this.incompatibleVersions$value == null) {
                this.incompatibleVersions$value = new HashSet<>();
                this.incompatibleVersions$set = true;
            }
            this.incompatibleVersions$value.add(version);
            return this;
        }
    }
}
