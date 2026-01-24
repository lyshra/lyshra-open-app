package com.lyshra.open.app.core.engine.audit.impl;

import com.lyshra.open.app.core.engine.audit.ILyshraOpenAppHumanTaskAuditService;
import com.lyshra.open.app.core.engine.audit.LyshraOpenAppHumanTaskAuditEntryModel;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskAuditEntry;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskAuditEntry.AuditAction;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the human task audit service.
 * Suitable for development, testing, and single-instance deployments.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Thread-safe storage using ConcurrentHashMap</li>
 *   <li>Multiple index support for fast queries</li>
 *   <li>JSON export capability</li>
 *   <li>Statistics tracking</li>
 * </ul>
 *
 * <h2>Production Considerations</h2>
 * <p>For production use, consider:</p>
 * <ul>
 *   <li>Database-backed implementation for persistence</li>
 *   <li>External audit log service integration (Splunk, ELK, etc.)</li>
 *   <li>Data retention policies</li>
 * </ul>
 */
@Slf4j
public class InMemoryHumanTaskAuditService implements ILyshraOpenAppHumanTaskAuditService {

    // Primary storage - entryId -> entry
    private final Map<String, ILyshraOpenAppHumanTaskAuditEntry> entries = new ConcurrentHashMap<>();

    // Indexes for fast lookups
    private final Map<String, List<String>> taskIndex = new ConcurrentHashMap<>();
    private final Map<String, List<String>> workflowIndex = new ConcurrentHashMap<>();
    private final Map<String, List<String>> actorIndex = new ConcurrentHashMap<>();
    private final Map<AuditAction, List<String>> actionIndex = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong totalLogged = new AtomicLong(0);
    private final Map<AuditAction, AtomicLong> actionCounts = new ConcurrentHashMap<>();

    public InMemoryHumanTaskAuditService() {
        log.info("InMemoryHumanTaskAuditService initialized");
    }

    // Singleton pattern
    private static final class SingletonHelper {
        private static final InMemoryHumanTaskAuditService INSTANCE = new InMemoryHumanTaskAuditService();
    }

    public static InMemoryHumanTaskAuditService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    // ========================================================================
    // AUDIT ENTRY LOGGING
    // ========================================================================

    @Override
    public Mono<ILyshraOpenAppHumanTaskAuditEntry> logAuditEntry(ILyshraOpenAppHumanTaskAuditEntry entry) {
        return Mono.fromCallable(() -> {
            String entryId = entry.getEntryId();
            entries.put(entryId, entry);

            // Update indexes
            indexEntry(entry);

            // Update statistics
            totalLogged.incrementAndGet();
            actionCounts.computeIfAbsent(entry.getAction(), k -> new AtomicLong(0)).incrementAndGet();

            log.debug("Audit entry logged: taskId={}, action={}, actor={}, timestamp={}",
                    getTaskId(entry), entry.getAction(), entry.getActorId(), entry.getTimestamp());

            return entry;
        });
    }

    @Override
    public Flux<ILyshraOpenAppHumanTaskAuditEntry> logAuditEntries(List<ILyshraOpenAppHumanTaskAuditEntry> entryList) {
        return Flux.fromIterable(entryList)
                .flatMap(this::logAuditEntry);
    }

    // ========================================================================
    // AUDIT TRAIL RETRIEVAL
    // ========================================================================

    @Override
    public Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditTrailForTask(String taskId) {
        return Mono.fromCallable(() -> {
            List<String> entryIds = taskIndex.getOrDefault(taskId, Collections.emptyList());
            return entryIds.stream()
                    .map(entries::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ILyshraOpenAppHumanTaskAuditEntry::getTimestamp))
                    .toList();
        }).flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditTrailForWorkflow(String workflowInstanceId) {
        return Mono.fromCallable(() -> {
            List<String> entryIds = workflowIndex.getOrDefault(workflowInstanceId, Collections.emptyList());
            return entryIds.stream()
                    .map(entries::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ILyshraOpenAppHumanTaskAuditEntry::getTimestamp))
                    .toList();
        }).flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<ILyshraOpenAppHumanTaskAuditEntry> getLatestAuditEntry(String taskId) {
        return getAuditTrailForTask(taskId)
                .collectList()
                .mapNotNull(list -> list.isEmpty() ? null : list.get(list.size() - 1));
    }

    @Override
    public Mono<ILyshraOpenAppHumanTaskAuditEntry> getAuditEntry(String entryId) {
        return Mono.justOrEmpty(entries.get(entryId));
    }

    // ========================================================================
    // AUDIT QUERY OPERATIONS
    // ========================================================================

    @Override
    public Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditEntriesByAction(AuditAction action) {
        return Mono.fromCallable(() -> {
            List<String> entryIds = actionIndex.getOrDefault(action, Collections.emptyList());
            return entryIds.stream()
                    .map(entries::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ILyshraOpenAppHumanTaskAuditEntry::getTimestamp))
                    .toList();
        }).flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditEntriesByActor(String actorId) {
        return Mono.fromCallable(() -> {
            List<String> entryIds = actorIndex.getOrDefault(actorId, Collections.emptyList());
            return entryIds.stream()
                    .map(entries::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ILyshraOpenAppHumanTaskAuditEntry::getTimestamp))
                    .toList();
        }).flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTaskAuditEntry> getAuditEntriesBetween(Instant from, Instant to) {
        return Mono.fromCallable(() ->
                entries.values().stream()
                        .filter(e -> !e.getTimestamp().isBefore(from) && e.getTimestamp().isBefore(to))
                        .sorted(Comparator.comparing(ILyshraOpenAppHumanTaskAuditEntry::getTimestamp))
                        .toList()
        ).flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<ILyshraOpenAppHumanTaskAuditEntry> queryAuditEntries(AuditQuery query) {
        return Mono.fromCallable(() -> {
            var stream = entries.values().stream();

            // Apply filters
            if (query.taskId() != null) {
                stream = stream.filter(e -> query.taskId().equals(getTaskId(e)));
            }
            if (query.workflowInstanceId() != null) {
                stream = stream.filter(e -> query.workflowInstanceId().equals(getWorkflowInstanceId(e)));
            }
            if (query.actorId() != null) {
                stream = stream.filter(e -> query.actorId().equals(e.getActorId()));
            }
            if (query.actions() != null && !query.actions().isEmpty()) {
                stream = stream.filter(e -> query.actions().contains(e.getAction()));
            }
            if (query.fromTimestamp() != null) {
                stream = stream.filter(e -> !e.getTimestamp().isBefore(query.fromTimestamp()));
            }
            if (query.toTimestamp() != null) {
                stream = stream.filter(e -> e.getTimestamp().isBefore(query.toTimestamp()));
            }
            if (query.correlationId() != null) {
                stream = stream.filter(e -> e.getCorrelationId().map(id -> id.equals(query.correlationId())).orElse(false));
            }

            // Sort
            Comparator<ILyshraOpenAppHumanTaskAuditEntry> comparator =
                    Comparator.comparing(ILyshraOpenAppHumanTaskAuditEntry::getTimestamp);
            if (query.sortOrder() == SortOrder.DESCENDING) {
                comparator = comparator.reversed();
            }
            stream = stream.sorted(comparator);

            // Apply pagination
            if (query.offset() != null && query.offset() > 0) {
                stream = stream.skip(query.offset());
            }
            if (query.limit() != null && query.limit() > 0) {
                stream = stream.limit(query.limit());
            }

            return stream.toList();
        }).flatMapMany(Flux::fromIterable);
    }

    // ========================================================================
    // AUDIT STATISTICS
    // ========================================================================

    @Override
    public Mono<Long> countAuditEntriesForTask(String taskId) {
        return Mono.fromCallable(() ->
                (long) taskIndex.getOrDefault(taskId, Collections.emptyList()).size());
    }

    @Override
    public Mono<Long> countAuditEntriesByAction(AuditAction action) {
        return Mono.fromCallable(() ->
                actionCounts.getOrDefault(action, new AtomicLong(0)).get());
    }

    @Override
    public Mono<Long> countAuditEntriesBetween(Instant from, Instant to) {
        return Mono.fromCallable(() ->
                entries.values().stream()
                        .filter(e -> !e.getTimestamp().isBefore(from) && e.getTimestamp().isBefore(to))
                        .count());
    }

    @Override
    public Mono<AuditStatistics> getAuditStatistics(Instant from, Instant to) {
        return Mono.fromCallable(() -> {
            List<ILyshraOpenAppHumanTaskAuditEntry> filtered = entries.values().stream()
                    .filter(e -> !e.getTimestamp().isBefore(from) && e.getTimestamp().isBefore(to))
                    .toList();

            Map<AuditAction, Long> byAction = filtered.stream()
                    .collect(Collectors.groupingBy(
                            ILyshraOpenAppHumanTaskAuditEntry::getAction,
                            Collectors.counting()));

            Map<String, Long> byActor = filtered.stream()
                    .collect(Collectors.groupingBy(
                            ILyshraOpenAppHumanTaskAuditEntry::getActorId,
                            Collectors.counting()));

            Map<String, Long> byTask = filtered.stream()
                    .filter(e -> getTaskId(e) != null)
                    .collect(Collectors.groupingBy(
                            this::getTaskId,
                            Collectors.counting()));

            long uniqueTasks = byTask.size();
            long uniqueActors = byActor.size();

            return new AuditStatistics(
                    filtered.size(),
                    byAction,
                    byActor,
                    byTask,
                    from,
                    to,
                    uniqueTasks,
                    uniqueActors
            );
        });
    }

    // ========================================================================
    // AUDIT EXPORT
    // ========================================================================

    @Override
    public Mono<AuditExportResult> exportAuditEntries(AuditQuery query, AuditExportFormat format) {
        return queryAuditEntries(query)
                .collectList()
                .map(entries -> {
                    String data;
                    try {
                        data = switch (format) {
                            case JSON -> exportToJson(entries);
                            case CSV -> exportToCsv(entries);
                            case XML -> exportToXml(entries);
                        };
                    } catch (Exception e) {
                        log.error("Failed to export audit entries", e);
                        data = "Export failed: " + e.getMessage();
                    }

                    return new AuditExportResult(
                            format.name(),
                            data,
                            entries.size(),
                            Instant.now(),
                            Map.of("query", query.toString())
                    );
                });
    }

    private String exportToJson(List<ILyshraOpenAppHumanTaskAuditEntry> entries) {
        StringBuilder json = new StringBuilder();
        json.append("[\n");

        for (int i = 0; i < entries.size(); i++) {
            ILyshraOpenAppHumanTaskAuditEntry entry = entries.get(i);
            json.append("  {\n");
            json.append("    \"entryId\": \"").append(escapeJson(entry.getEntryId())).append("\",\n");
            json.append("    \"timestamp\": \"").append(entry.getTimestamp()).append("\",\n");
            json.append("    \"taskId\": ").append(getTaskId(entry) != null ? "\"" + escapeJson(getTaskId(entry)) + "\"" : "null").append(",\n");
            json.append("    \"workflowInstanceId\": ").append(getWorkflowInstanceId(entry) != null ? "\"" + escapeJson(getWorkflowInstanceId(entry)) + "\"" : "null").append(",\n");
            json.append("    \"action\": \"").append(entry.getAction()).append("\",\n");
            json.append("    \"actorId\": \"").append(escapeJson(entry.getActorId())).append("\",\n");
            json.append("    \"actorType\": \"").append(entry.getActorType()).append("\",\n");
            json.append("    \"description\": \"").append(escapeJson(entry.getDescription())).append("\",\n");
            json.append("    \"previousStatus\": ").append(entry.getPreviousStatus().map(s -> "\"" + s.name() + "\"").orElse("null")).append(",\n");
            json.append("    \"newStatus\": ").append(entry.getNewStatus().map(s -> "\"" + s.name() + "\"").orElse("null")).append("\n");
            json.append("  }");
            if (i < entries.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("]");
        return json.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String exportToCsv(List<ILyshraOpenAppHumanTaskAuditEntry> entries) {
        StringBuilder csv = new StringBuilder();
        csv.append("EntryId,Timestamp,TaskId,Action,ActorId,ActorType,Description,PreviousStatus,NewStatus\n");

        for (ILyshraOpenAppHumanTaskAuditEntry entry : entries) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,\"%s\",%s,%s%n",
                    entry.getEntryId(),
                    entry.getTimestamp(),
                    getTaskId(entry),
                    entry.getAction(),
                    entry.getActorId(),
                    entry.getActorType(),
                    entry.getDescription().replace("\"", "\"\""),
                    entry.getPreviousStatus().map(Enum::name).orElse(""),
                    entry.getNewStatus().map(Enum::name).orElse("")
            ));
        }

        return csv.toString();
    }

    private String exportToXml(List<ILyshraOpenAppHumanTaskAuditEntry> entries) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<auditEntries>\n");

        for (ILyshraOpenAppHumanTaskAuditEntry entry : entries) {
            xml.append("  <entry>\n");
            xml.append("    <entryId>").append(entry.getEntryId()).append("</entryId>\n");
            xml.append("    <timestamp>").append(entry.getTimestamp()).append("</timestamp>\n");
            xml.append("    <taskId>").append(getTaskId(entry)).append("</taskId>\n");
            xml.append("    <action>").append(entry.getAction()).append("</action>\n");
            xml.append("    <actorId>").append(entry.getActorId()).append("</actorId>\n");
            xml.append("    <actorType>").append(entry.getActorType()).append("</actorType>\n");
            xml.append("    <description>").append(escapeXml(entry.getDescription())).append("</description>\n");
            entry.getPreviousStatus().ifPresent(s ->
                    xml.append("    <previousStatus>").append(s).append("</previousStatus>\n"));
            entry.getNewStatus().ifPresent(s ->
                    xml.append("    <newStatus>").append(s).append("</newStatus>\n"));
            xml.append("  </entry>\n");
        }

        xml.append("</auditEntries>");
        return xml.toString();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // ========================================================================
    // AUDIT MAINTENANCE
    // ========================================================================

    @Override
    public Mono<Long> deleteAuditEntriesBefore(Instant before) {
        return Mono.fromCallable(() -> {
            List<String> toDelete = entries.values().stream()
                    .filter(e -> e.getTimestamp().isBefore(before))
                    .map(ILyshraOpenAppHumanTaskAuditEntry::getEntryId)
                    .toList();

            for (String entryId : toDelete) {
                ILyshraOpenAppHumanTaskAuditEntry entry = entries.remove(entryId);
                if (entry != null) {
                    removeFromIndexes(entry);
                }
            }

            log.info("Deleted {} audit entries before {}", toDelete.size(), before);
            return (long) toDelete.size();
        });
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Resets the service (for testing).
     */
    public void reset() {
        entries.clear();
        taskIndex.clear();
        workflowIndex.clear();
        actorIndex.clear();
        actionIndex.clear();
        totalLogged.set(0);
        actionCounts.clear();
        log.info("InMemoryHumanTaskAuditService reset");
    }

    /**
     * Gets total logged entries count.
     */
    public long getTotalLoggedCount() {
        return totalLogged.get();
    }

    /**
     * Gets current entry count.
     */
    public int getEntryCount() {
        return entries.size();
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private void indexEntry(ILyshraOpenAppHumanTaskAuditEntry entry) {
        String entryId = entry.getEntryId();

        // Index by task
        String taskId = getTaskId(entry);
        if (taskId != null) {
            taskIndex.computeIfAbsent(taskId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(entryId);
        }

        // Index by workflow
        String workflowId = getWorkflowInstanceId(entry);
        if (workflowId != null) {
            workflowIndex.computeIfAbsent(workflowId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(entryId);
        }

        // Index by actor
        if (entry.getActorId() != null) {
            actorIndex.computeIfAbsent(entry.getActorId(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(entryId);
        }

        // Index by action
        actionIndex.computeIfAbsent(entry.getAction(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(entryId);
    }

    private void removeFromIndexes(ILyshraOpenAppHumanTaskAuditEntry entry) {
        String entryId = entry.getEntryId();

        String taskId = getTaskId(entry);
        if (taskId != null) {
            List<String> taskEntries = taskIndex.get(taskId);
            if (taskEntries != null) taskEntries.remove(entryId);
        }

        String workflowId = getWorkflowInstanceId(entry);
        if (workflowId != null) {
            List<String> workflowEntries = workflowIndex.get(workflowId);
            if (workflowEntries != null) workflowEntries.remove(entryId);
        }

        if (entry.getActorId() != null) {
            List<String> actorEntries = actorIndex.get(entry.getActorId());
            if (actorEntries != null) actorEntries.remove(entryId);
        }

        List<String> actionEntries = actionIndex.get(entry.getAction());
        if (actionEntries != null) actionEntries.remove(entryId);
    }

    private String getTaskId(ILyshraOpenAppHumanTaskAuditEntry entry) {
        if (entry instanceof LyshraOpenAppHumanTaskAuditEntryModel model) {
            return model.getTaskId();
        }
        // Try to get from data map
        return entry.getData().containsKey("taskId")
                ? String.valueOf(entry.getData().get("taskId"))
                : null;
    }

    private String getWorkflowInstanceId(ILyshraOpenAppHumanTaskAuditEntry entry) {
        if (entry instanceof LyshraOpenAppHumanTaskAuditEntryModel model) {
            return model.getWorkflowInstanceId();
        }
        // Try to get from data map
        return entry.getData().containsKey("workflowInstanceId")
                ? String.valueOf(entry.getData().get("workflowInstanceId"))
                : null;
    }
}
