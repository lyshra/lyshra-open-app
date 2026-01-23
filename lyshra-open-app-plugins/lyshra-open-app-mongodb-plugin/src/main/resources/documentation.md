# MongoDB Plugin for Lyshra OpenApp

## Overview

The MongoDB Plugin provides comprehensive MongoDB database integration for the Lyshra OpenApp workflow execution engine. It offers high-performance, reactive operations for all MongoDB CRUD operations, aggregation pipelines, index management, and transaction support.

**Plugin Identifier:** `com.lyshra.open.app:mongodb:1.0.0`

## Features

- **Full CRUD Operations**: Find, Insert, Update, Delete with single and batch support
- **Aggregation Pipeline**: Complete support for MongoDB aggregation framework
- **Index Management**: Create, drop, and list indexes
- **Transaction Support**: ACID transactions for multi-document operations
- **Reactive Operations**: Non-blocking async execution using Project Reactor
- **Connection Pooling**: Efficient connection management with multi-tenancy support
- **BSON Type Handling**: Automatic conversion between Java and MongoDB types

## Connection Configuration

Configure MongoDB connections in your workflow context:

```yaml
mongodbConnection:
  connectionString: "mongodb://localhost:27017"
  database: "myapp"
  connectionPoolSettings:
    maxPoolSize: 100
    minPoolSize: 10
    maxWaitTimeMs: 120000
  readPreference: "PRIMARY"
  readConcern: "MAJORITY"
  writeConcern: "w:majority"
  retryWrites: true
  retryReads: true
```

### Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| connectionString | String | Required | MongoDB connection URI |
| database | String | Required | Default database name |
| username | String | Optional | Authentication username |
| password | String | Optional | Authentication password |
| connectionPoolSettings.maxPoolSize | Integer | 100 | Maximum connections |
| connectionPoolSettings.minPoolSize | Integer | 0 | Minimum connections |
| connectionPoolSettings.maxWaitTimeMs | Integer | 120000 | Max wait for connection |
| readPreference | Enum | PRIMARY | Read preference |
| readConcern | Enum | LOCAL | Read consistency level |
| writeConcern | String | "w:1" | Write acknowledgment |
| retryWrites | Boolean | true | Retry write operations |
| retryReads | Boolean | true | Retry read operations |

## Processors

### Query Processors

#### MONGO_FIND_PROCESSOR
Retrieves multiple documents matching the filter criteria.

```java
.processor(MongoFindProcessor::build)
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "orders",
    "filter", Map.of("status", "pending"),
    "projection", Map.of("_id", 1, "orderId", 1, "amount", 1),
    "sort", Map.of("createdAt", -1),
    "limit", 10,
    "skip", 0
))
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| connectionConfigKey | String | Yes | Configuration key |
| collection | String | Yes | Collection name |
| filter | Map | No | Query filter |
| projection | Map<String, Integer> | No | Fields to include/exclude |
| sort | Map<String, Integer> | No | Sort order (1/-1) |
| limit | Integer | No | Max documents |
| skip | Integer | No | Documents to skip |
| hint | Object | No | Index hint |
| allowDiskUse | Boolean | No | Allow disk for large sorts |
| maxTimeMs | Integer | No | Query timeout |

#### MONGO_FIND_ONE_PROCESSOR
Retrieves a single document.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "users",
    "filter", Map.of("_id", Map.of("$oid", "507f1f77bcf86cd799439011"))
))
```

#### MONGO_COUNT_PROCESSOR
Counts documents matching the filter.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "users",
    "filter", Map.of("status", "active")
))
```

#### MONGO_DISTINCT_PROCESSOR
Gets distinct values for a field.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "orders",
    "fieldName", "status"
))
```

### Write Processors

#### MONGO_INSERT_ONE_PROCESSOR
Inserts a single document.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "users",
    "document", Map.of(
        "name", "John Doe",
        "email", "john@example.com"
    ),
    "returnInsertedId", true
))
```

#### MONGO_INSERT_MANY_PROCESSOR
Inserts multiple documents.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "users",
    "documents", List.of(
        Map.of("name", "Alice"),
        Map.of("name", "Bob")
    ),
    "ordered", true
))
```

#### MONGO_UPDATE_ONE_PROCESSOR
Updates a single document.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "users",
    "filter", Map.of("email", "john@example.com"),
    "update", Map.of(
        "$set", Map.of("status", "active"),
        "$inc", Map.of("loginCount", 1)
    ),
    "upsert", false
))
```

#### MONGO_UPDATE_MANY_PROCESSOR
Updates multiple documents.

#### MONGO_REPLACE_ONE_PROCESSOR
Replaces a document entirely.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "users",
    "filter", Map.of("email", "old@example.com"),
    "replacement", Map.of(
        "name", "New Name",
        "email", "new@example.com"
    )
))
```

#### MONGO_DELETE_ONE_PROCESSOR
Deletes a single document.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "users",
    "filter", Map.of("_id", Map.of("$oid", "507f1f77bcf86cd799439011"))
))
```

#### MONGO_DELETE_MANY_PROCESSOR
Deletes multiple documents.

### Atomic Operations

#### MONGO_FIND_ONE_AND_UPDATE_PROCESSOR
Atomically finds and updates a document.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "counters",
    "filter", Map.of("_id", "orderId"),
    "update", Map.of("$inc", Map.of("seq", 1)),
    "returnDocument", "AFTER",
    "upsert", true
))
```

#### MONGO_FIND_ONE_AND_REPLACE_PROCESSOR
Atomically finds and replaces a document.

#### MONGO_FIND_ONE_AND_DELETE_PROCESSOR
Atomically finds and deletes a document.

### Aggregation

#### MONGO_AGGREGATE_PROCESSOR
Executes aggregation pipelines.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "orders",
    "pipeline", List.of(
        Map.of("$match", Map.of("status", "completed")),
        Map.of("$group", Map.of(
            "_id", "$region",
            "totalSales", Map.of("$sum", "$amount"),
            "orderCount", Map.of("$sum", 1)
        )),
        Map.of("$sort", Map.of("totalSales", -1)),
        Map.of("$limit", 10)
    ),
    "allowDiskUse", true
))
```

### Bulk Operations

#### MONGO_BULK_WRITE_PROCESSOR
Executes multiple write operations in a single batch.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "users",
    "operations", List.of(
        Map.of(
            "operationType", "INSERT_ONE",
            "document", Map.of("name", "Alice")
        ),
        Map.of(
            "operationType", "UPDATE_ONE",
            "filter", Map.of("name", "Bob"),
            "update", Map.of("$set", Map.of("active", true))
        ),
        Map.of(
            "operationType", "DELETE_ONE",
            "filter", Map.of("status", "deleted")
        )
    ),
    "ordered", true
))
```

### Index Management

#### MONGO_CREATE_INDEX_PROCESSOR
Creates an index on a collection.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "users",
    "indexKeys", Map.of("email", 1),
    "indexName", "email_unique_idx",
    "unique", true,
    "sparse", false
))
```

Index types supported:
- Single field: `Map.of("field", 1)` or `Map.of("field", -1)`
- Compound: `Map.of("field1", 1, "field2", -1)`
- Text: `Map.of("field", "text")`
- Geospatial: `Map.of("location", "2dsphere")`
- Hashed: `Map.of("field", "hashed")`
- TTL: Use `expireAfterSeconds` option

#### MONGO_DROP_INDEX_PROCESSOR
Drops an index.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "collection", "users",
    "indexName", "email_unique_idx"
))
```

#### MONGO_LIST_INDEXES_PROCESSOR
Lists all indexes on a collection.

### Transaction Support

#### MONGO_TRANSACTION_PROCESSOR
Executes multiple operations in an ACID transaction.

```java
.inputConfig(Map.of(
    "connectionConfigKey", "mongodbConnection",
    "database", "ecommerce",
    "operations", List.of(
        Map.of(
            "operationType", "INSERT_ONE",
            "collection", "orders",
            "document", Map.of("orderId", "ORD-001", "amount", 150.00)
        ),
        Map.of(
            "operationType", "UPDATE_ONE",
            "collection", "inventory",
            "filter", Map.of("productId", "PROD-001"),
            "update", Map.of("$inc", Map.of("quantity", -1))
        )
    ),
    "maxCommitTimeMs", 30000,
    "readConcern", "snapshot",
    "writeConcern", "majority"
))
```

## BSON Type Handling

The plugin automatically converts between Java and MongoDB BSON types:

| Java Type | BSON Type | Special Syntax |
|-----------|-----------|----------------|
| String | String | - |
| Integer | Int32 | `{"$numberInt": "123"}` |
| Long | Int64 | `{"$numberLong": "123"}` |
| Double | Double | `{"$numberDouble": "123.45"}` |
| BigDecimal | Decimal128 | `{"$numberDecimal": "123.45"}` |
| Date/Instant | Date | `{"$date": 1234567890000}` |
| ObjectId | ObjectId | `{"$oid": "507f1f77bcf86cd799439011"}` |
| byte[] | Binary | `{"$binary": "..."}` |
| Pattern | Regex | `{"$regex": "pattern", "$options": "i"}` |

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| MONGO_001 | 503 | Connection failed |
| MONGO_002 | 400 | Connection config not found |
| MONGO_003 | 400 | Invalid connection string |
| MONGO_004 | 401 | Authentication failed |
| MONGO_021 | 500 | Query execution failed |
| MONGO_022 | 400 | Invalid filter |
| MONGO_041 | 500 | Write failed |
| MONGO_042 | 409 | Duplicate key error |
| MONGO_061 | 400 | Invalid aggregation pipeline |
| MONGO_081 | 500 | Index creation failed |
| MONGO_101 | 500 | Transaction failed |

## Best Practices

1. **Connection Pooling**: Configure appropriate pool sizes based on workload
2. **Indexes**: Create indexes for frequently queried fields
3. **Projections**: Use projections to limit data transfer
4. **Batch Operations**: Use bulk write for multiple operations
5. **Transactions**: Use transactions for multi-document atomicity
6. **Error Handling**: Configure retry policies in workflows
7. **Read Preferences**: Use secondary reads for analytics queries

## Requirements

- MongoDB 4.0+ (for transaction support)
- Replica set or sharded cluster (for transactions)
- Java 21+
- Lyshra OpenApp Core Engine
