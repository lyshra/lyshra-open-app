package com.lyshra.open.app.distributed.config;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.distributed.cluster.IClusterCoordinator;
import com.lyshra.open.app.distributed.cluster.impl.AbstractClusterCoordinator;
import com.lyshra.open.app.distributed.executor.IDistributedWorkflowExecutor;
import com.lyshra.open.app.distributed.executor.impl.DistributedWorkflowExecutorImpl;
import com.lyshra.open.app.distributed.lock.IDistributedLockManager;
import com.lyshra.open.app.distributed.lock.impl.InMemoryDistributedLockManager;
import com.lyshra.open.app.distributed.membership.INodeMembershipService;
import com.lyshra.open.app.distributed.membership.impl.HeartbeatMembershipService;
import com.lyshra.open.app.distributed.ownership.IWorkflowOwnershipManager;
import com.lyshra.open.app.distributed.ownership.impl.LeaseBasedOwnershipManager;
import com.lyshra.open.app.distributed.sharding.IPartitionManager;
import com.lyshra.open.app.distributed.sharding.IShardingStrategy;
import com.lyshra.open.app.distributed.sharding.impl.ConsistentHashShardingStrategy;
import com.lyshra.open.app.distributed.sharding.impl.DefaultPartitionManager;
import com.lyshra.open.app.distributed.state.IWorkflowStateStore;
import com.lyshra.open.app.distributed.state.impl.InMemoryWorkflowStateStore;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Factory for creating and wiring distributed workflow engine components.
 *
 * This factory provides:
 * - Component instantiation with proper dependencies
 * - Lifecycle management (initialization and shutdown)
 * - Configuration-based customization
 *
 * Usage:
 * <pre>
 * DistributedConfig config = DistributedConfig.production("node-1", "host1", 8080, 256);
 * DistributedWorkflowEngine engine = DistributedWorkflowEngineFactory.create(config);
 * engine.initialize().block();
 * // Use engine.getDistributedExecutor() for workflow execution
 * engine.shutdown().block();
 * </pre>
 *
 * Design Pattern: Factory Pattern - encapsulates complex object creation.
 */
@Slf4j
public final class DistributedWorkflowEngineFactory {

    private DistributedWorkflowEngineFactory() {
        // Utility class
    }

    /**
     * Creates a fully configured distributed workflow engine.
     *
     * @param config the configuration
     * @return the configured engine
     */
    public static DistributedWorkflowEngine create(DistributedConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        config.validate();

        log.info("Creating distributed workflow engine with config: {}", config);

        // Create core components
        ILyshraOpenAppFacade facade = LyshraOpenAppFacade.getInstance();

        // Create cluster coordinator (using simple in-memory implementation)
        IClusterCoordinator clusterCoordinator = createClusterCoordinator(config);

        // Create sharding strategy
        IShardingStrategy shardingStrategy = createShardingStrategy(config);

        // Create partition manager
        IPartitionManager partitionManager = new DefaultPartitionManager(
                clusterCoordinator,
                shardingStrategy,
                config.getTotalPartitions()
        );

        // Create lock manager
        IDistributedLockManager lockManager = new InMemoryDistributedLockManager(clusterCoordinator);

        // Create ownership manager
        IWorkflowOwnershipManager ownershipManager = new LeaseBasedOwnershipManager(
                clusterCoordinator,
                partitionManager,
                lockManager
        );

        // Create state store
        IWorkflowStateStore stateStore = createStateStore(config);

        // Create membership service
        INodeMembershipService membershipService = new HeartbeatMembershipService(
                clusterCoordinator,
                config.getHeartbeatInterval(),
                config.getFailureDetectionTimeout()
        );

        // Create distributed executor
        IDistributedWorkflowExecutor distributedExecutor = new DistributedWorkflowExecutorImpl(
                clusterCoordinator,
                partitionManager,
                shardingStrategy,
                ownershipManager,
                stateStore,
                facade
        );

        return new DistributedWorkflowEngine(
                config,
                clusterCoordinator,
                partitionManager,
                lockManager,
                ownershipManager,
                stateStore,
                membershipService,
                distributedExecutor,
                facade
        );
    }

    /**
     * Creates a cluster coordinator based on configuration.
     */
    private static IClusterCoordinator createClusterCoordinator(DistributedConfig config) {
        // Simple in-memory coordinator for single-node or testing
        return new SimpleInMemoryClusterCoordinator(
                config.getNodeId(),
                config.getHostname(),
                config.getPort()
        );
    }

    /**
     * Creates a sharding strategy based on configuration.
     */
    private static IShardingStrategy createShardingStrategy(DistributedConfig config) {
        return switch (config.getShardingStrategy()) {
            case "consistent-hash" -> new ConsistentHashShardingStrategy();
            default -> new ConsistentHashShardingStrategy();
        };
    }

    /**
     * Creates a state store based on configuration.
     */
    private static IWorkflowStateStore createStateStore(DistributedConfig config) {
        return switch (config.getStateStoreType()) {
            case "in-memory" -> new InMemoryWorkflowStateStore();
            default -> new InMemoryWorkflowStateStore();
        };
    }

    /**
     * Simple in-memory cluster coordinator for testing and single-node deployments.
     */
    private static class SimpleInMemoryClusterCoordinator extends AbstractClusterCoordinator {

        SimpleInMemoryClusterCoordinator(String nodeId, String hostname, int port) {
            super(nodeId, hostname, port);
        }

        @Override
        protected Mono<Void> doInitialize() {
            // Single node - we are the only member and leader
            activeNodes.set(Set.of(nodeId));
            updateLeader(nodeId);
            return Mono.empty();
        }

        @Override
        protected Mono<Void> doShutdown() {
            activeNodes.set(Set.of());
            updateLeader(null);
            return Mono.empty();
        }

        @Override
        protected Mono<Void> doHeartbeat() {
            return Mono.empty();
        }

        @Override
        protected Mono<Optional<String>> doLeaderElection() {
            return Mono.just(Optional.of(nodeId));
        }

        @Override
        protected Mono<Set<String>> doFetchActiveNodes() {
            return Mono.just(Set.of(nodeId));
        }
    }
}
