package com.lyshra.open.app.integration.contract.humantask;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a comment or note added to a human task.
 * Comments provide an audit trail of communication during task processing.
 */
public interface ILyshraOpenAppHumanTaskComment {

    /**
     * Unique identifier for this comment.
     */
    String getCommentId();

    /**
     * User who added the comment.
     */
    String getAuthor();

    /**
     * Comment text content.
     */
    String getContent();

    /**
     * When the comment was created.
     */
    Instant getCreatedAt();

    /**
     * When the comment was last edited (if edited).
     */
    Instant getEditedAt();

    /**
     * Whether the comment is visible to all or internal only.
     */
    boolean isInternal();

    /**
     * Attachments associated with the comment.
     */
    List<ILyshraOpenAppHumanTaskAttachment> getAttachments();

    /**
     * Mentions of users in the comment.
     */
    List<String> getMentions();

    /**
     * Additional metadata.
     */
    Map<String, Object> getMetadata();

    /**
     * Represents an attachment to a comment.
     */
    interface ILyshraOpenAppHumanTaskAttachment {

        /**
         * Attachment identifier.
         */
        String getAttachmentId();

        /**
         * File name.
         */
        String getFileName();

        /**
         * MIME type.
         */
        String getContentType();

        /**
         * File size in bytes.
         */
        long getSize();

        /**
         * Storage location or URL.
         */
        String getStorageUrl();

        /**
         * Upload timestamp.
         */
        Instant getUploadedAt();

        /**
         * Uploader user.
         */
        String getUploadedBy();
    }
}
