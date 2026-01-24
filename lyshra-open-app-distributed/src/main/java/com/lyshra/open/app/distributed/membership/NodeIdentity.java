package com.lyshra.open.app.distributed.membership;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the unique identity of a node in the distributed cluster.
 *
 * A NodeIdentity provides:
 * - Globally unique node ID
 * - Machine identification (hostname, IP, MAC)
 * - Process identification (PID)
 * - Instance identification (for multiple instances on same machine)
 *
 * The node ID is generated using a combination of:
 * - Machine identifier (hostname or IP)
 * - Process ID
 * - Random UUID component
 * - Timestamp
 *
 * This ensures uniqueness even when:
 * - Multiple instances run on the same machine
 * - Instances restart quickly
 * - Containers are recreated with same hostname
 *
 * Thread Safety: This class is immutable and thread-safe.
 *
 * Usage:
 * <pre>
 * // Auto-generate identity
 * NodeIdentity identity = NodeIdentity.generate();
 *
 * // Or with custom prefix
 * NodeIdentity identity = NodeIdentity.generate("workflow-engine");
 *
 * // Or fully custom
 * NodeIdentity identity = NodeIdentity.builder()
 *     .nodeId("custom-node-1")
 *     .hostname("localhost")
 *     .build();
 * </pre>
 */
@Getter
@Builder(toBuilder = true)
@ToString
public final class NodeIdentity {

    /**
     * The unique node identifier used for ownership and coordination.
     */
    private final String nodeId;

    /**
     * The hostname of the machine running this node.
     */
    private final String hostname;

    /**
     * The IP address of this node.
     */
    private final String ipAddress;

    /**
     * The port this node listens on (if applicable).
     */
    private final int port;

    /**
     * The process ID of this node.
     */
    private final long processId;

    /**
     * A unique instance identifier for this JVM instance.
     */
    private final String instanceId;

    /**
     * When this identity was created.
     */
    private final Instant createdAt;

    /**
     * The datacenter or region this node is in (if known).
     */
    private final String datacenter;

    /**
     * The availability zone this node is in (if known).
     */
    private final String availabilityZone;

    /**
     * The rack or placement group this node is in (if known).
     */
    private final String rack;

    /**
     * Version of the application running on this node.
     */
    private final String applicationVersion;

    /**
     * Custom tags for this node (e.g., "role=worker", "tier=premium").
     */
    private final java.util.Map<String, String> tags;

    private NodeIdentity(String nodeId, String hostname, String ipAddress, int port,
                        long processId, String instanceId, Instant createdAt,
                        String datacenter, String availabilityZone, String rack,
                        String applicationVersion, java.util.Map<String, String> tags) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.hostname = hostname != null ? hostname : "unknown";
        this.ipAddress = ipAddress;
        this.port = port;
        this.processId = processId;
        this.instanceId = instanceId != null ? instanceId : UUID.randomUUID().toString();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.datacenter = datacenter;
        this.availabilityZone = availabilityZone;
        this.rack = rack;
        this.applicationVersion = applicationVersion;
        this.tags = tags != null ?
                java.util.Collections.unmodifiableMap(tags) :
                java.util.Collections.emptyMap();
    }

    // ========== Factory Methods ==========

    /**
     * Generates a unique node identity with auto-detected machine info.
     *
     * @return a new NodeIdentity
     */
    public static NodeIdentity generate() {
        return generate(null);
    }

    /**
     * Generates a unique node identity with a custom prefix.
     *
     * @param prefix optional prefix for the node ID
     * @return a new NodeIdentity
     */
    public static NodeIdentity generate(String prefix) {
        String hostname = detectHostname();
        String ipAddress = detectIpAddress();
        long pid = detectProcessId();
        String instanceId = UUID.randomUUID().toString().substring(0, 8);

        // Generate node ID: prefix-hostname-pid-instanceId
        String nodeId;
        if (prefix != null && !prefix.isEmpty()) {
            nodeId = String.format("%s-%s-%d-%s", prefix, sanitize(hostname), pid, instanceId);
        } else {
            nodeId = String.format("%s-%d-%s", sanitize(hostname), pid, instanceId);
        }

        return NodeIdentity.builder()
                .nodeId(nodeId)
                .hostname(hostname)
                .ipAddress(ipAddress)
                .processId(pid)
                .instanceId(instanceId)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Generates a node identity using UUID.
     *
     * @return a new NodeIdentity with UUID-based node ID
     */
    public static NodeIdentity generateUUID() {
        String hostname = detectHostname();
        String ipAddress = detectIpAddress();
        long pid = detectProcessId();
        String uuid = UUID.randomUUID().toString();

        return NodeIdentity.builder()
                .nodeId(uuid)
                .hostname(hostname)
                .ipAddress(ipAddress)
                .processId(pid)
                .instanceId(uuid)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Creates a node identity with a specific node ID.
     *
     * @param nodeId the node ID to use
     * @return a new NodeIdentity
     */
    public static NodeIdentity withId(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return NodeIdentity.builder()
                .nodeId(nodeId)
                .hostname(detectHostname())
                .ipAddress(detectIpAddress())
                .processId(detectProcessId())
                .instanceId(UUID.randomUUID().toString().substring(0, 8))
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Creates a node identity for testing purposes.
     *
     * @param nodeId the test node ID
     * @return a new NodeIdentity for testing
     */
    public static NodeIdentity forTesting(String nodeId) {
        return NodeIdentity.builder()
                .nodeId(nodeId)
                .hostname("test-host")
                .ipAddress("127.0.0.1")
                .processId(1)
                .instanceId("test")
                .createdAt(Instant.now())
                .build();
    }

    // ========== Detection Methods ==========

    private static String detectHostname() {
        try {
            // Try to get hostname from environment first (works in containers)
            String envHostname = System.getenv("HOSTNAME");
            if (envHostname != null && !envHostname.isEmpty()) {
                return envHostname;
            }

            // Fall back to Java's hostname detection
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            // Last resort: use a generated identifier
            return "node-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private static String detectIpAddress() {
        try {
            // Try to find a non-loopback address
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }

            // Fall back to localhost
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static long detectProcessId() {
        try {
            String processName = ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(processName.split("@")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String sanitize(String input) {
        if (input == null) {
            return "unknown";
        }
        // Replace non-alphanumeric characters with dashes
        return input.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    // ========== Query Methods ==========

    /**
     * Gets the full address in host:port format.
     *
     * @return the address string
     */
    public String getAddress() {
        if (port > 0) {
            return (ipAddress != null ? ipAddress : hostname) + ":" + port;
        }
        return ipAddress != null ? ipAddress : hostname;
    }

    /**
     * Gets a tag value.
     *
     * @param key the tag key
     * @return Optional containing the tag value
     */
    public java.util.Optional<String> getTag(String key) {
        return java.util.Optional.ofNullable(tags.get(key));
    }

    /**
     * Checks if this identity has a specific tag.
     *
     * @param key the tag key
     * @return true if the tag exists
     */
    public boolean hasTag(String key) {
        return tags.containsKey(key);
    }

    /**
     * Gets the short node ID (first 8 characters or less).
     *
     * @return the short node ID
     */
    public String getShortId() {
        return nodeId.length() > 12 ? nodeId.substring(0, 12) : nodeId;
    }

    /**
     * Checks if this identity appears to be from the same machine as another.
     *
     * @param other the other identity
     * @return true if likely same machine
     */
    public boolean isSameMachine(NodeIdentity other) {
        if (other == null) {
            return false;
        }
        // Same if hostname and IP match
        return Objects.equals(this.hostname, other.hostname) &&
               Objects.equals(this.ipAddress, other.ipAddress);
    }

    /**
     * Checks if this identity is from the same process as another.
     *
     * @param other the other identity
     * @return true if same process
     */
    public boolean isSameProcess(NodeIdentity other) {
        if (other == null) {
            return false;
        }
        return isSameMachine(other) && this.processId == other.processId;
    }

    /**
     * Creates a copy with updated port.
     *
     * @param newPort the new port
     * @return a new NodeIdentity with updated port
     */
    public NodeIdentity withPort(int newPort) {
        return this.toBuilder().port(newPort).build();
    }

    /**
     * Creates a copy with additional tag.
     *
     * @param key the tag key
     * @param value the tag value
     * @return a new NodeIdentity with the tag added
     */
    public NodeIdentity withTag(String key, String value) {
        java.util.Map<String, String> newTags = new java.util.HashMap<>(this.tags);
        newTags.put(key, value);
        return this.toBuilder().tags(newTags).build();
    }

    /**
     * Creates a copy with datacenter info.
     *
     * @param datacenter the datacenter
     * @param availabilityZone the availability zone
     * @return a new NodeIdentity with location info
     */
    public NodeIdentity withLocation(String datacenter, String availabilityZone) {
        return this.toBuilder()
                .datacenter(datacenter)
                .availabilityZone(availabilityZone)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeIdentity that = (NodeIdentity) o;
        return Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }
}
