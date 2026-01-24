package com.lyshra.open.app.integration.models.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of workflow version following SemVer 2.0.0 specification.
 * Immutable value object representing a specific workflow version.
 */
@Data
@Builder
public class WorkflowVersion implements IWorkflowVersion, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
            "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
            "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;
    private final Instant createdAt;
    private final boolean deprecated;
    private final String deprecationReason;

    @Override
    public Optional<String> getPreRelease() {
        return Optional.ofNullable(preRelease);
    }

    @Override
    public Optional<String> getBuildMetadata() {
        return Optional.ofNullable(buildMetadata);
    }

    @Override
    public Optional<String> getDeprecationReason() {
        return Optional.ofNullable(deprecationReason);
    }

    @Override
    public String toVersionString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (preRelease != null && !preRelease.isEmpty()) {
            sb.append('-').append(preRelease);
        }
        if (buildMetadata != null && !buildMetadata.isEmpty()) {
            sb.append('+').append(buildMetadata);
        }
        return sb.toString();
    }

    @Override
    public int compareTo(IWorkflowVersion other) {
        // Compare major, minor, patch
        int result = Integer.compare(this.major, other.getMajor());
        if (result != 0) return result;

        result = Integer.compare(this.minor, other.getMinor());
        if (result != 0) return result;

        result = Integer.compare(this.patch, other.getPatch());
        if (result != 0) return result;

        // Pre-release versions have lower precedence
        boolean thisHasPreRelease = this.preRelease != null && !this.preRelease.isEmpty();
        boolean otherHasPreRelease = other.getPreRelease().isPresent();

        if (thisHasPreRelease && !otherHasPreRelease) return -1;
        if (!thisHasPreRelease && otherHasPreRelease) return 1;
        if (thisHasPreRelease && otherHasPreRelease) {
            return comparePreRelease(this.preRelease, other.getPreRelease().get());
        }

        return 0;
    }

    private int comparePreRelease(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int length = Math.max(partsA.length, partsB.length);

        for (int i = 0; i < length; i++) {
            if (i >= partsA.length) return -1;
            if (i >= partsB.length) return 1;

            String partA = partsA[i];
            String partB = partsB[i];

            boolean aIsNumeric = partA.matches("\\d+");
            boolean bIsNumeric = partB.matches("\\d+");

            if (aIsNumeric && bIsNumeric) {
                int result = Integer.compare(Integer.parseInt(partA), Integer.parseInt(partB));
                if (result != 0) return result;
            } else if (aIsNumeric) {
                return -1;
            } else if (bIsNumeric) {
                return 1;
            } else {
                int result = partA.compareTo(partB);
                if (result != 0) return result;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof IWorkflowVersion)) return false;
        IWorkflowVersion that = (IWorkflowVersion) o;
        return major == that.getMajor() &&
               minor == that.getMinor() &&
               patch == that.getPatch() &&
               Objects.equals(preRelease, that.getPreRelease().orElse(null));
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    @Override
    public String toString() {
        return toVersionString();
    }

    /**
     * Parses a version string into a WorkflowVersion.
     *
     * @param versionString version string (e.g., "1.2.3-beta+build.123")
     * @return parsed version
     * @throws IllegalArgumentException if invalid format
     */
    public static WorkflowVersion parse(String versionString) {
        Matcher matcher = SEMVER_PATTERN.matcher(versionString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version string: " + versionString);
        }

        return WorkflowVersion.builder()
                .major(Integer.parseInt(matcher.group(1)))
                .minor(Integer.parseInt(matcher.group(2)))
                .patch(Integer.parseInt(matcher.group(3)))
                .preRelease(matcher.group(4))
                .buildMetadata(matcher.group(5))
                .createdAt(Instant.now())
                .deprecated(false)
                .build();
    }

    /**
     * Creates a version with the given components.
     *
     * @param major major version
     * @param minor minor version
     * @param patch patch version
     * @return new version
     */
    public static WorkflowVersion of(int major, int minor, int patch) {
        return WorkflowVersion.builder()
                .major(major)
                .minor(minor)
                .patch(patch)
                .createdAt(Instant.now())
                .deprecated(false)
                .build();
    }

    /**
     * Creates a new version with incremented major.
     *
     * @return new version
     */
    public WorkflowVersion incrementMajor() {
        return WorkflowVersion.builder()
                .major(this.major + 1)
                .minor(0)
                .patch(0)
                .createdAt(Instant.now())
                .deprecated(false)
                .build();
    }

    /**
     * Creates a new version with incremented minor.
     *
     * @return new version
     */
    public WorkflowVersion incrementMinor() {
        return WorkflowVersion.builder()
                .major(this.major)
                .minor(this.minor + 1)
                .patch(0)
                .createdAt(Instant.now())
                .deprecated(false)
                .build();
    }

    /**
     * Creates a new version with incremented patch.
     *
     * @return new version
     */
    public WorkflowVersion incrementPatch() {
        return WorkflowVersion.builder()
                .major(this.major)
                .minor(this.minor)
                .patch(this.patch + 1)
                .createdAt(Instant.now())
                .deprecated(false)
                .build();
    }

    /**
     * Creates a deprecated copy of this version.
     *
     * @param reason deprecation reason
     * @return deprecated version
     */
    public WorkflowVersion deprecate(String reason) {
        return WorkflowVersion.builder()
                .major(this.major)
                .minor(this.minor)
                .patch(this.patch)
                .preRelease(this.preRelease)
                .buildMetadata(this.buildMetadata)
                .createdAt(this.createdAt)
                .deprecated(true)
                .deprecationReason(reason)
                .build();
    }
}
