package com.lyshra.open.app.core.engine.version.util;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflow;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Utility class for computing SHA-256 schema hashes of workflow definitions.
 *
 * <p>The schema hash provides:</p>
 * <ul>
 *   <li>Integrity verification - detect unauthorized changes</li>
 *   <li>Change detection - identify when definitions have been modified</li>
 *   <li>Fast equality comparison - compare definitions by hash</li>
 *   <li>Audit trail - track definition changes over time</li>
 * </ul>
 *
 * <p>The hash is computed from a canonical string representation that includes:</p>
 * <ul>
 *   <li>Workflow name and start step</li>
 *   <li>All step definitions (sorted by name for consistency)</li>
 *   <li>Step types, processors, input configs, branching logic</li>
 *   <li>Error handling and timeout configurations</li>
 * </ul>
 *
 * <p>Note: The hash intentionally excludes version-specific metadata (version number,
 * createdAt, etc.) to focus on the structural definition of the workflow.</p>
 */
@Slf4j
public final class WorkflowSchemaHashUtil {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final char FIELD_SEPARATOR = '|';
    private static final char ENTRY_SEPARATOR = ';';
    private static final char MAP_KEY_VALUE_SEPARATOR = '=';

    private WorkflowSchemaHashUtil() {
        // Utility class - no instantiation
    }

    /**
     * Computes the SHA-256 hash of a workflow definition's schema.
     *
     * @param workflow workflow definition to hash
     * @return SHA-256 hash as 64-character hexadecimal string
     * @throws IllegalStateException if hash computation fails
     */
    public static String computeSchemaHash(ILyshraOpenAppWorkflow workflow) {
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow cannot be null");
        }

        String canonicalRepresentation = buildCanonicalRepresentation(workflow);
        return computeSha256(canonicalRepresentation);
    }

    /**
     * Computes the SHA-256 hash of a versioned workflow definition's schema.
     * Excludes version metadata to focus on structural changes.
     *
     * @param workflow versioned workflow definition to hash
     * @return SHA-256 hash as 64-character hexadecimal string
     */
    public static String computeSchemaHash(IVersionedWorkflow workflow) {
        if (workflow == null) {
            throw new IllegalArgumentException("Workflow cannot be null");
        }

        // Build canonical representation including versioned workflow specific fields
        StringBuilder sb = new StringBuilder();
        sb.append("VERSIONED_WORKFLOW").append(FIELD_SEPARATOR);
        sb.append("workflowId=").append(nullSafe(workflow.getWorkflowId())).append(FIELD_SEPARATOR);
        sb.append("schemaVersion=").append(nullSafe(workflow.getSchemaVersion())).append(FIELD_SEPARATOR);
        sb.append(buildCanonicalRepresentation(workflow));

        return computeSha256(sb.toString());
    }

    /**
     * Verifies that a workflow's schema matches the expected hash.
     *
     * @param workflow workflow to verify
     * @param expectedHash expected SHA-256 hash
     * @return true if hash matches
     */
    public static boolean verifySchemaHash(ILyshraOpenAppWorkflow workflow, String expectedHash) {
        if (workflow == null || expectedHash == null) {
            return false;
        }
        String actualHash = computeSchemaHash(workflow);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    /**
     * Verifies that a versioned workflow's schema matches the expected hash.
     *
     * @param workflow versioned workflow to verify
     * @param expectedHash expected SHA-256 hash
     * @return true if hash matches
     */
    public static boolean verifySchemaHash(IVersionedWorkflow workflow, String expectedHash) {
        if (workflow == null || expectedHash == null) {
            return false;
        }
        String actualHash = computeSchemaHash(workflow);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    /**
     * Builds a canonical string representation of a workflow for hashing.
     * Uses sorted maps to ensure consistent ordering.
     */
    private static String buildCanonicalRepresentation(ILyshraOpenAppWorkflow workflow) {
        StringBuilder sb = new StringBuilder();

        // Workflow-level fields
        sb.append("name=").append(nullSafe(workflow.getName())).append(FIELD_SEPARATOR);
        sb.append("startStep=").append(nullSafe(workflow.getStartStep())).append(FIELD_SEPARATOR);
        sb.append("contextRetention=").append(nullSafe(workflow.getContextRetention())).append(FIELD_SEPARATOR);

        // Steps (sorted by name for consistency)
        sb.append("steps=[");
        if (workflow.getSteps() != null && !workflow.getSteps().isEmpty()) {
            SortedMap<String, ILyshraOpenAppWorkflowStep> sortedSteps = new TreeMap<>(workflow.getSteps());
            boolean first = true;
            for (Map.Entry<String, ILyshraOpenAppWorkflowStep> entry : sortedSteps.entrySet()) {
                if (!first) {
                    sb.append(ENTRY_SEPARATOR);
                }
                first = false;
                sb.append(buildStepRepresentation(entry.getKey(), entry.getValue()));
            }
        }
        sb.append("]");

        return sb.toString();
    }

    /**
     * Builds a canonical string representation of a workflow step.
     */
    private static String buildStepRepresentation(String stepName, ILyshraOpenAppWorkflowStep step) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("name=").append(nullSafe(stepName)).append(FIELD_SEPARATOR);
        sb.append("type=").append(nullSafe(step.getType())).append(FIELD_SEPARATOR);
        sb.append("description=").append(nullSafe(step.getDescription())).append(FIELD_SEPARATOR);

        // Processor reference
        if (step.getProcessor() != null) {
            sb.append("processor=").append(step.getProcessor().toString()).append(FIELD_SEPARATOR);
        }

        // Workflow call reference
        if (step.getWorkflowCall() != null) {
            sb.append("workflowCall=").append(step.getWorkflowCall().toString()).append(FIELD_SEPARATOR);
        }

        // Input configuration (sorted for consistency)
        sb.append("inputConfig=");
        if (step.getInputConfig() != null && !step.getInputConfig().isEmpty()) {
            sb.append(buildSortedMapRepresentation(step.getInputConfig()));
        } else {
            sb.append("{}");
        }
        sb.append(FIELD_SEPARATOR);

        // Timeout
        if (step.getTimeout() != null) {
            sb.append("timeout=").append(step.getTimeout().toMillis()).append(FIELD_SEPARATOR);
        }

        // Next step branching
        if (step.getNext() != null && step.getNext().getBranches() != null) {
            sb.append("branches=").append(buildSortedMapRepresentation(step.getNext().getBranches())).append(FIELD_SEPARATOR);
        }

        // Error handling
        if (step.getOnError() != null && step.getOnError().getErrorConfigs() != null) {
            sb.append("onError=").append(buildSortedMapRepresentation(step.getOnError().getErrorConfigs()));
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Builds a sorted string representation of a map.
     */
    private static String buildSortedMapRepresentation(Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder("{");
        SortedMap<String, ?> sortedMap = new TreeMap<>(map);
        boolean first = true;
        for (Map.Entry<String, ?> entry : sortedMap.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(entry.getKey()).append(MAP_KEY_VALUE_SEPARATOR);
            sb.append(valueToString(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Converts a value to its canonical string representation.
     */
    private static String valueToString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, ?> mapValue = (Map<String, ?>) value;
            return buildSortedMapRepresentation(mapValue);
        }
        if (value instanceof Iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(valueToString(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return value.toString();
    }

    /**
     * Computes SHA-256 hash of the input string.
     */
    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts byte array to hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Returns string representation of object or "null" if null.
     */
    private static String nullSafe(Object value) {
        return value == null ? "null" : value.toString();
    }
}
