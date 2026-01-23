package com.lyshra.open.app.mongodb.plugin.error;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MongoDB Processor Error Codes enumeration.
 * Defines all error codes for MongoDB operations with proper HTTP status mappings,
 * user-friendly messages, and resolution guidance.
 */
@Getter
@AllArgsConstructor
public enum MongoProcessorErrorCodes implements ILyshraOpenAppErrorInfo {

    // Connection Errors (MONGO_001 - MONGO_010)
    CONNECTION_FAILED(
            "MONGO_001",
            LyshraOpenAppHttpStatus.SERVICE_UNAVAILABLE,
            "Failed to connect to MongoDB: {message}",
            "Verify the connection string is correct and MongoDB server is accessible"
    ),
    CONNECTION_CONFIG_NOT_FOUND(
            "MONGO_002",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "MongoDB connection configuration not found for key: {configKey}",
            "Ensure the connection configuration is defined in the context or system config"
    ),
    INVALID_CONNECTION_STRING(
            "MONGO_003",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid MongoDB connection string: {message}",
            "Verify the connection string format follows MongoDB URI specification"
    ),
    AUTHENTICATION_FAILED(
            "MONGO_004",
            LyshraOpenAppHttpStatus.UNAUTHORIZED,
            "MongoDB authentication failed: {message}",
            "Verify username, password, and authentication database are correct"
    ),
    CONNECTION_POOL_EXHAUSTED(
            "MONGO_005",
            LyshraOpenAppHttpStatus.SERVICE_UNAVAILABLE,
            "MongoDB connection pool exhausted, waited {waitTimeMs}ms",
            "Increase maxPoolSize or reduce concurrent operations"
    ),
    CONNECTION_TIMEOUT(
            "MONGO_006",
            LyshraOpenAppHttpStatus.GATEWAY_TIMEOUT,
            "MongoDB connection timed out after {timeoutMs}ms",
            "Increase connection timeout or check network connectivity"
    ),
    SSL_HANDSHAKE_FAILED(
            "MONGO_007",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "MongoDB SSL/TLS handshake failed: {message}",
            "Verify SSL certificates and TLS configuration"
    ),
    SERVER_SELECTION_FAILED(
            "MONGO_008",
            LyshraOpenAppHttpStatus.SERVICE_UNAVAILABLE,
            "MongoDB server selection failed: {message}",
            "Ensure MongoDB replica set members are available and accessible"
    ),

    // Database/Collection Errors (MONGO_011 - MONGO_020)
    DATABASE_NOT_FOUND(
            "MONGO_011",
            LyshraOpenAppHttpStatus.NOT_FOUND,
            "Database not found: {database}",
            "Verify the database name is correct and exists on the server"
    ),
    COLLECTION_NOT_FOUND(
            "MONGO_012",
            LyshraOpenAppHttpStatus.NOT_FOUND,
            "Collection not found: {collection} in database: {database}",
            "Verify the collection name is correct; it will be created on first write"
    ),
    INVALID_DATABASE_NAME(
            "MONGO_013",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid database name: {database}",
            "Database names cannot contain: /\\. \"$ or be empty"
    ),
    INVALID_COLLECTION_NAME(
            "MONGO_014",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid collection name: {collection}",
            "Collection names cannot contain $ or null character, and cannot be empty"
    ),

    // Query Errors (MONGO_021 - MONGO_040)
    QUERY_EXECUTION_FAILED(
            "MONGO_021",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "MongoDB query execution failed: {message}",
            "Check query syntax and ensure filter/projection are valid"
    ),
    INVALID_FILTER(
            "MONGO_022",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid MongoDB filter: {message}",
            "Verify the filter document uses valid MongoDB query operators"
    ),
    INVALID_PROJECTION(
            "MONGO_023",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid projection: {message}",
            "Projection must use 1 (include) or 0 (exclude) consistently except for _id"
    ),
    INVALID_SORT(
            "MONGO_024",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid sort specification: {message}",
            "Sort must use 1 (ascending) or -1 (descending) for each field"
    ),
    QUERY_TIMEOUT(
            "MONGO_025",
            LyshraOpenAppHttpStatus.GATEWAY_TIMEOUT,
            "MongoDB query timed out after {maxTimeMs}ms",
            "Optimize query with indexes or increase maxTimeMs"
    ),
    CURSOR_NOT_FOUND(
            "MONGO_026",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "MongoDB cursor not found or expired",
            "Reduce batch size or process results faster"
    ),

    // Write Errors (MONGO_041 - MONGO_060)
    WRITE_FAILED(
            "MONGO_041",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "MongoDB write operation failed: {message}",
            "Check document structure and write concern settings"
    ),
    DUPLICATE_KEY_ERROR(
            "MONGO_042",
            LyshraOpenAppHttpStatus.CONFLICT,
            "Duplicate key error on field: {field}, value: {value}",
            "Ensure unique index constraints are satisfied; use upsert if intended"
    ),
    DOCUMENT_VALIDATION_FAILED(
            "MONGO_043",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Document validation failed: {message}",
            "Ensure document meets collection schema validation rules"
    ),
    INVALID_UPDATE_OPERATION(
            "MONGO_044",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid update operation: {message}",
            "Use valid update operators ($set, $inc, $push, etc.) or provide replacement document"
    ),
    WRITE_CONCERN_ERROR(
            "MONGO_045",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "Write concern error: {message}",
            "Adjust write concern or ensure sufficient replica set members"
    ),
    DOCUMENT_TOO_LARGE(
            "MONGO_046",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Document exceeds maximum size of 16MB",
            "Reduce document size or use GridFS for large files"
    ),
    EMPTY_FILTER_FOR_DELETE(
            "MONGO_047",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Empty filter not allowed for delete operation",
            "Provide a filter to prevent accidental deletion of all documents"
    ),

    // Aggregation Errors (MONGO_061 - MONGO_080)
    INVALID_AGGREGATION_PIPELINE(
            "MONGO_061",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid aggregation pipeline: {message}",
            "Verify pipeline stages use valid aggregation operators"
    ),
    AGGREGATION_EXECUTION_FAILED(
            "MONGO_062",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "Aggregation execution failed: {message}",
            "Check pipeline stages and ensure sufficient memory/disk space"
    ),
    AGGREGATION_TIMEOUT(
            "MONGO_063",
            LyshraOpenAppHttpStatus.GATEWAY_TIMEOUT,
            "Aggregation timed out after {maxTimeMs}ms",
            "Optimize pipeline, add indexes, or enable allowDiskUse"
    ),
    INVALID_AGGREGATION_STAGE(
            "MONGO_064",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid aggregation stage: {stage}",
            "Use valid aggregation stage operators ($match, $group, $project, etc.)"
    ),

    // Index Errors (MONGO_081 - MONGO_100)
    INDEX_CREATION_FAILED(
            "MONGO_081",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to create index: {message}",
            "Check index specification and ensure sufficient disk space"
    ),
    INDEX_NOT_FOUND(
            "MONGO_082",
            LyshraOpenAppHttpStatus.NOT_FOUND,
            "Index not found: {indexName}",
            "Verify the index name is correct; use listIndexes to see available indexes"
    ),
    INDEX_DROP_FAILED(
            "MONGO_083",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to drop index: {message}",
            "Verify index name and ensure no operations are using the index"
    ),
    INVALID_INDEX_SPECIFICATION(
            "MONGO_084",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid index specification: {message}",
            "Verify index keys and options are valid"
    ),
    DUPLICATE_INDEX(
            "MONGO_085",
            LyshraOpenAppHttpStatus.CONFLICT,
            "Index already exists: {indexName}",
            "Index with same keys already exists; use different name or skip creation"
    ),

    // Transaction Errors (MONGO_101 - MONGO_120)
    TRANSACTION_FAILED(
            "MONGO_101",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "Transaction failed: {message}",
            "Check operation validity and retry transaction if transient error"
    ),
    TRANSACTION_TIMEOUT(
            "MONGO_102",
            LyshraOpenAppHttpStatus.GATEWAY_TIMEOUT,
            "Transaction timed out after {maxCommitTimeMs}ms",
            "Reduce transaction duration or increase maxCommitTimeMs"
    ),
    TRANSACTION_ABORTED(
            "MONGO_103",
            LyshraOpenAppHttpStatus.CONFLICT,
            "Transaction aborted due to conflict: {message}",
            "Retry transaction; concurrent modification detected"
    ),
    TRANSACTION_NOT_SUPPORTED(
            "MONGO_104",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Transactions not supported: {message}",
            "Ensure MongoDB version 4.0+ and replica set or sharded cluster"
    ),
    SESSION_EXPIRED(
            "MONGO_105",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Client session expired",
            "Create a new session and retry operation"
    ),

    // Bulk Operation Errors (MONGO_121 - MONGO_140)
    BULK_WRITE_FAILED(
            "MONGO_121",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "Bulk write operation failed: {successCount} succeeded, {failCount} failed",
            "Check individual errors in result and retry failed operations"
    ),
    INVALID_BULK_OPERATION(
            "MONGO_122",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid bulk operation type: {operationType}",
            "Use valid operation types: INSERT_ONE, UPDATE_ONE, UPDATE_MANY, REPLACE_ONE, DELETE_ONE, DELETE_MANY"
    ),

    // Validation Errors (MONGO_141 - MONGO_160)
    VALIDATION_ERROR(
            "MONGO_141",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Input validation failed: {message}",
            "Check input parameters and ensure required fields are provided"
    ),
    MISSING_REQUIRED_FIELD(
            "MONGO_142",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Missing required field: {field}",
            "Provide the required field in the input configuration"
    ),
    INVALID_FIELD_VALUE(
            "MONGO_143",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid value for field {field}: {message}",
            "Provide a valid value for the field"
    ),

    // BSON/Type Conversion Errors (MONGO_161 - MONGO_180)
    BSON_CONVERSION_FAILED(
            "MONGO_161",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Failed to convert value to BSON: {message}",
            "Check the data type compatibility with MongoDB BSON types"
    ),
    INVALID_OBJECT_ID(
            "MONGO_162",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Invalid ObjectId format: {value}",
            "ObjectId must be a 24-character hex string"
    ),
    TYPE_CONVERSION_ERROR(
            "MONGO_163",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "Type conversion error: {message}",
            "Ensure the value can be converted to the target type"
    ),

    // Network Errors (MONGO_181 - MONGO_200)
    NETWORK_ERROR(
            "MONGO_181",
            LyshraOpenAppHttpStatus.SERVICE_UNAVAILABLE,
            "MongoDB network error: {message}",
            "Check network connectivity and firewall settings"
    ),
    SOCKET_TIMEOUT(
            "MONGO_182",
            LyshraOpenAppHttpStatus.GATEWAY_TIMEOUT,
            "MongoDB socket timeout: {message}",
            "Increase socket timeout or check network latency"
    );

    private final String errorCode;
    private final LyshraOpenAppHttpStatus httpStatus;
    private final String errorTemplate;
    private final String resolutionTemplate;
}
