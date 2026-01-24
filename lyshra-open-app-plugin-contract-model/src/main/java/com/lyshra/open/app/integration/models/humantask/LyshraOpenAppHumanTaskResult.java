package com.lyshra.open.app.integration.models.humantask;

import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskResult;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of ILyshraOpenAppHumanTaskResult.
 * Used to carry the outcome of human task resolution.
 */
@Data
@Builder
public class LyshraOpenAppHumanTaskResult implements ILyshraOpenAppHumanTaskResult {

    private final String branch;
    private final Object data;
    private final LyshraOpenAppHumanTaskStatus taskStatus;
    private final String taskId;
    private final String resolvedBy;
    private final Instant resolvedAt;
    private final Map<String, Object> formData;
    private final String resolutionComment;
    private final boolean autoResolved;
    private final String autoResolutionReason;
    private final long durationMillis;
    private final int escalationCount;

    @Override
    public Optional<String> getResolvedBy() {
        return Optional.ofNullable(resolvedBy);
    }

    @Override
    public Optional<Map<String, Object>> getFormData() {
        return Optional.ofNullable(formData);
    }

    @Override
    public Optional<String> getResolutionComment() {
        return Optional.ofNullable(resolutionComment);
    }

    @Override
    public Optional<String> getAutoResolutionReason() {
        return Optional.ofNullable(autoResolutionReason);
    }

    /**
     * Factory method for creating an approved result.
     */
    public static LyshraOpenAppHumanTaskResult approved(String taskId, String resolvedBy, long durationMillis) {
        return LyshraOpenAppHumanTaskResult.builder()
                .branch("APPROVED")
                .taskStatus(LyshraOpenAppHumanTaskStatus.APPROVED)
                .taskId(taskId)
                .resolvedBy(resolvedBy)
                .resolvedAt(Instant.now())
                .autoResolved(false)
                .durationMillis(durationMillis)
                .build();
    }

    /**
     * Factory method for creating a rejected result.
     */
    public static LyshraOpenAppHumanTaskResult rejected(String taskId, String resolvedBy, String reason, long durationMillis) {
        return LyshraOpenAppHumanTaskResult.builder()
                .branch("REJECTED")
                .taskStatus(LyshraOpenAppHumanTaskStatus.REJECTED)
                .taskId(taskId)
                .resolvedBy(resolvedBy)
                .resolvedAt(Instant.now())
                .resolutionComment(reason)
                .autoResolved(false)
                .durationMillis(durationMillis)
                .build();
    }

    /**
     * Factory method for creating a completed result with form data.
     */
    public static LyshraOpenAppHumanTaskResult completed(
            String taskId,
            String resolvedBy,
            Map<String, Object> formData,
            long durationMillis) {

        return LyshraOpenAppHumanTaskResult.builder()
                .branch("DEFAULT")
                .data(formData)
                .taskStatus(LyshraOpenAppHumanTaskStatus.COMPLETED)
                .taskId(taskId)
                .resolvedBy(resolvedBy)
                .resolvedAt(Instant.now())
                .formData(formData)
                .autoResolved(false)
                .durationMillis(durationMillis)
                .build();
    }

    /**
     * Factory method for creating a timed-out result.
     */
    public static LyshraOpenAppHumanTaskResult timedOut(String taskId, String reason, long durationMillis) {
        return LyshraOpenAppHumanTaskResult.builder()
                .branch("TIMED_OUT")
                .taskStatus(LyshraOpenAppHumanTaskStatus.TIMED_OUT)
                .taskId(taskId)
                .resolvedAt(Instant.now())
                .autoResolved(true)
                .autoResolutionReason(reason)
                .durationMillis(durationMillis)
                .build();
    }
}
