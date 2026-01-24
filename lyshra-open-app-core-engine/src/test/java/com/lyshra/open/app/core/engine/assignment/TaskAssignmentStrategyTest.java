package com.lyshra.open.app.core.engine.assignment;

import com.lyshra.open.app.core.engine.assignment.strategies.*;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for task assignment strategies.
 */
@DisplayName("Task Assignment Strategy Tests")
class TaskAssignmentStrategyTest {

    @Nested
    @DisplayName("Direct Assignee Strategy Tests")
    class DirectAssigneeStrategyTests {

        private DirectAssigneeStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new DirectAssigneeStrategy();
        }

        @Test
        @DisplayName("Should assign to configured users")
        void shouldAssignToConfiguredUsers() {
            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .strategyConfig(AssignmentStrategyConfig.directTo(List.of("user1", "user2")))
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.ASSIGNED, result.getResultType());
                        assertEquals(List.of("user1", "user2"), result.getAssignees());
                        assertEquals("user1", result.getPrimaryAssignee());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fall back to context candidates when no config")
        void shouldFallBackToContextCandidates() {
            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .candidateUsers(List.of("contextUser1", "contextUser2"))
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.ASSIGNED, result.getResultType());
                        assertTrue(result.getAssignees().contains("contextUser1"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should filter excluded users")
        void shouldFilterExcludedUsers() {
            AssignmentStrategyConfig config = AssignmentStrategyConfig.builder()
                    .strategyType(AssignmentStrategyConfig.StrategyType.DIRECT)
                    .assigneeUsers(List.of("user1", "user2", "user3"))
                    .excludeInitiator(true)
                    .build();

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .strategyConfig(config)
                    .workflowInitiator("user2")
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.ASSIGNED, result.getResultType());
                        assertFalse(result.getAssignees().contains("user2"));
                        assertTrue(result.getAssignees().contains("user1"));
                        assertTrue(result.getAssignees().contains("user3"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fall back to groups when no assignees")
        void shouldFallBackToGroups() {
            AssignmentStrategyConfig config = AssignmentStrategyConfig.builder()
                    .strategyType(AssignmentStrategyConfig.StrategyType.DIRECT)
                    .assigneeUsers(List.of())
                    .fallbackToGroups(true)
                    .build();

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .strategyConfig(config)
                    .candidateGroups(List.of("approvers", "managers"))
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.CANDIDATE_GROUPS, result.getResultType());
                        assertTrue(result.getCandidateGroups().contains("approvers"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return no assignees when none available")
        void shouldReturnNoAssigneesWhenNoneAvailable() {
            AssignmentStrategyConfig config = AssignmentStrategyConfig.builder()
                    .strategyType(AssignmentStrategyConfig.StrategyType.DIRECT)
                    .assigneeUsers(List.of())
                    .fallbackToGroups(false)
                    .build();

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .strategyConfig(config)
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.NO_ASSIGNEES, result.getResultType());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Role-Based Assignment Strategy Tests")
    class RoleBasedAssignmentStrategyTests {

        private RoleBasedAssignmentStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new RoleBasedAssignmentStrategy();
        }

        @Test
        @DisplayName("Should assign to candidate groups")
        void shouldAssignToCandidateGroups() {
            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .strategyConfig(AssignmentStrategyConfig.roleBased(List.of("approvers", "managers")))
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.CANDIDATE_GROUPS, result.getResultType());
                        assertEquals(List.of("approvers", "managers"), result.getCandidateGroups());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should use role member resolver when available")
        void shouldUseRoleMemberResolver() {
            RoleBasedAssignmentStrategy.RoleMemberResolver resolver =
                    (role, ctx) -> Mono.just(List.of("user_" + role + "_1", "user_" + role + "_2"));

            strategy = new RoleBasedAssignmentStrategy(resolver);

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .strategyConfig(AssignmentStrategyConfig.roleBased(List.of("approvers")))
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.MIXED, result.getResultType());
                        assertTrue(result.getAssignees().contains("user_approvers_1"));
                        assertTrue(result.getAssignees().contains("user_approvers_2"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should filter excluded roles")
        void shouldFilterExcludedRoles() {
            AssignmentStrategyConfig config = AssignmentStrategyConfig.builder()
                    .strategyType(AssignmentStrategyConfig.StrategyType.ROLE_BASED)
                    .assigneeRoles(List.of("approvers", "managers", "admins"))
                    .excludedRoles(List.of("admins"))
                    .build();

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .strategyConfig(config)
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.CANDIDATE_GROUPS, result.getResultType());
                        assertFalse(result.getCandidateGroups().contains("admins"));
                        assertEquals(2, result.getCandidateGroups().size());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Round-Robin Assignment Strategy Tests")
    class RoundRobinAssignmentStrategyTests {

        private RoundRobinAssignmentStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new RoundRobinAssignmentStrategy();
            strategy.resetAllRotations();
        }

        @Test
        @DisplayName("Should rotate through users")
        void shouldRotateThroughUsers() {
            List<String> userPool = List.of("user1", "user2", "user3");
            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .strategyConfig(AssignmentStrategyConfig.roundRobin(userPool))
                    .build();

            // First assignment should be user1
            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> assertEquals("user1", result.getPrimaryAssignee()))
                    .verifyComplete();

            // Second assignment should be user2
            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> assertEquals("user2", result.getPrimaryAssignee()))
                    .verifyComplete();

            // Third assignment should be user3
            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> assertEquals("user3", result.getPrimaryAssignee()))
                    .verifyComplete();

            // Fourth assignment should wrap to user1
            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> assertEquals("user1", result.getPrimaryAssignee()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should use tracking key for independent rotations")
        void shouldUseTrackingKeyForIndependentRotations() {
            List<String> userPool = List.of("user1", "user2");

            AssignmentContext context1 = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .strategyConfig(AssignmentStrategyConfig.roundRobin(userPool, "workflow-A"))
                    .build();

            AssignmentContext context2 = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .strategyConfig(AssignmentStrategyConfig.roundRobin(userPool, "workflow-B"))
                    .build();

            // First assignment for workflow-A should be user1
            StepVerifier.create(strategy.assign(context1))
                    .assertNext(result -> assertEquals("user1", result.getPrimaryAssignee()))
                    .verifyComplete();

            // First assignment for workflow-B should also be user1 (independent rotation)
            StepVerifier.create(strategy.assign(context2))
                    .assertNext(result -> assertEquals("user1", result.getPrimaryAssignee()))
                    .verifyComplete();

            // Second assignment for workflow-A should be user2
            StepVerifier.create(strategy.assign(context1))
                    .assertNext(result -> assertEquals("user2", result.getPrimaryAssignee()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should filter excluded users in rotation")
        void shouldFilterExcludedUsersInRotation() {
            AssignmentStrategyConfig config = AssignmentStrategyConfig.builder()
                    .strategyType(AssignmentStrategyConfig.StrategyType.ROUND_ROBIN)
                    .assigneePool(List.of("user1", "user2", "user3"))
                    .excludeInitiator(true)
                    .build();

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .strategyConfig(config)
                    .workflowInitiator("user2")
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        // user2 should be excluded from rotation
                        assertFalse(result.getAssignees().contains("user2"));
                        assertEquals(2, result.getAssignees().size());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Load-Balanced Assignment Strategy Tests")
    class LoadBalancedAssignmentStrategyTests {

        private LoadBalancedAssignmentStrategy strategy;
        private Map<String, LoadBalancedAssignmentStrategy.UserTaskLoad> mockLoads;

        @BeforeEach
        void setUp() {
            mockLoads = new ConcurrentHashMap<>();
            LoadBalancedAssignmentStrategy.TaskLoadProvider mockProvider =
                    (userId, tenantId) -> Mono.just(
                            mockLoads.getOrDefault(userId,
                                    LoadBalancedAssignmentStrategy.UserTaskLoad.empty(userId)));

            strategy = new LoadBalancedAssignmentStrategy(mockProvider);
        }

        @Test
        @DisplayName("Should assign to user with least load")
        void shouldAssignToUserWithLeastLoad() {
            mockLoads.put("user1", new LoadBalancedAssignmentStrategy.UserTaskLoad("user1", 5, 2, 7.0, true));
            mockLoads.put("user2", new LoadBalancedAssignmentStrategy.UserTaskLoad("user2", 2, 1, 3.0, true));
            mockLoads.put("user3", new LoadBalancedAssignmentStrategy.UserTaskLoad("user3", 8, 1, 9.0, true));

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .strategyConfig(AssignmentStrategyConfig.loadBalanced(
                            List.of("user1", "user2", "user3")))
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.ASSIGNED, result.getResultType());
                        assertEquals("user2", result.getPrimaryAssignee()); // Least loaded
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should respect max tasks per user")
        void shouldRespectMaxTasksPerUser() {
            mockLoads.put("user1", new LoadBalancedAssignmentStrategy.UserTaskLoad("user1", 10, 0, 10.0, true));
            mockLoads.put("user2", new LoadBalancedAssignmentStrategy.UserTaskLoad("user2", 8, 0, 8.0, true));
            mockLoads.put("user3", new LoadBalancedAssignmentStrategy.UserTaskLoad("user3", 3, 0, 3.0, true));

            AssignmentStrategyConfig config = AssignmentStrategyConfig.builder()
                    .strategyType(AssignmentStrategyConfig.StrategyType.LOAD_BALANCED)
                    .assigneePool(List.of("user1", "user2", "user3"))
                    .maxTasksPerUser(5)
                    .build();

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .taskTitle("Test Task")
                    .strategyConfig(config)
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.ASSIGNED, result.getResultType());
                        assertEquals("user3", result.getPrimaryAssignee()); // Only one below threshold
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should assign to least loaded even when all above threshold")
        void shouldAssignToLeastLoadedEvenWhenAllAboveThreshold() {
            mockLoads.put("user1", new LoadBalancedAssignmentStrategy.UserTaskLoad("user1", 10, 0, 10.0, true));
            mockLoads.put("user2", new LoadBalancedAssignmentStrategy.UserTaskLoad("user2", 8, 0, 8.0, true));

            AssignmentStrategyConfig config = AssignmentStrategyConfig.builder()
                    .strategyType(AssignmentStrategyConfig.StrategyType.LOAD_BALANCED)
                    .assigneePool(List.of("user1", "user2"))
                    .maxTasksPerUser(5)
                    .allowDeferred(false)
                    .build();

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .strategyConfig(config)
                    .build();

            StepVerifier.create(strategy.assign(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.ASSIGNED, result.getResultType());
                        assertEquals("user2", result.getPrimaryAssignee()); // Least loaded
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Strategy Registry Tests")
    class StrategyRegistryTests {

        private LyshraOpenAppTaskAssignmentStrategyRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new LyshraOpenAppTaskAssignmentStrategyRegistry();
        }

        @Test
        @DisplayName("Should have default strategies registered")
        void shouldHaveDefaultStrategiesRegistered() {
            assertTrue(registry.hasStrategy("DIRECT"));
            assertTrue(registry.hasStrategy("ROLE_BASED"));
            assertTrue(registry.hasStrategy("ROUND_ROBIN"));
            assertTrue(registry.hasStrategy("LOAD_BALANCED"));
        }

        @Test
        @DisplayName("Should get strategy by name")
        void shouldGetStrategyByName() {
            assertTrue(registry.getStrategy("DIRECT").isPresent());
            assertEquals("DIRECT", registry.getStrategy("DIRECT").get().getStrategyName());
        }

        @Test
        @DisplayName("Should get strategy for config")
        void shouldGetStrategyForConfig() {
            AssignmentStrategyConfig config = AssignmentStrategyConfig.roundRobin(List.of("user1"));
            ILyshraOpenAppTaskAssignmentStrategy strategy = registry.getStrategyForConfig(config);
            assertEquals("ROUND_ROBIN", strategy.getStrategyName());
        }

        @Test
        @DisplayName("Should register custom strategy")
        void shouldRegisterCustomStrategy() {
            ILyshraOpenAppTaskAssignmentStrategy customStrategy = new ILyshraOpenAppTaskAssignmentStrategy() {
                @Override
                public String getStrategyName() {
                    return "CUSTOM_TEST";
                }

                @Override
                public String getDescription() {
                    return "Test custom strategy";
                }

                @Override
                public Mono<AssignmentResult> assign(AssignmentContext context) {
                    return Mono.just(AssignmentResult.assignTo("customUser"));
                }
            };

            registry.register(customStrategy);
            assertTrue(registry.hasStrategy("CUSTOM_TEST"));

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .build();

            StepVerifier.create(registry.getStrategy("CUSTOM_TEST").get().assign(context))
                    .assertNext(result -> assertEquals("customUser", result.getPrimaryAssignee()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should execute assignment through registry")
        void shouldExecuteAssignmentThroughRegistry() {
            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .strategyConfig(AssignmentStrategyConfig.directTo(List.of("registryUser")))
                    .build();

            StepVerifier.create(registry.executeAssignment(context))
                    .assertNext(result -> {
                        assertEquals(AssignmentResult.ResultType.ASSIGNED, result.getResultType());
                        assertEquals("registryUser", result.getPrimaryAssignee());
                        assertEquals("DIRECT", result.getStrategyName());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Assignment Result Tests")
    class AssignmentResultTests {

        @Test
        @DisplayName("Should create assigned result")
        void shouldCreateAssignedResult() {
            AssignmentResult result = AssignmentResult.assignTo(List.of("user1", "user2"));
            assertEquals(AssignmentResult.ResultType.ASSIGNED, result.getResultType());
            assertEquals("user1", result.getPrimaryAssignee());
            assertTrue(result.isSuccessful());
            assertTrue(result.hasAssignees());
        }

        @Test
        @DisplayName("Should create candidate groups result")
        void shouldCreateCandidateGroupsResult() {
            AssignmentResult result = AssignmentResult.toGroups(List.of("group1", "group2"));
            assertEquals(AssignmentResult.ResultType.CANDIDATE_GROUPS, result.getResultType());
            assertTrue(result.isSuccessful());
            assertTrue(result.hasCandidateGroups());
        }

        @Test
        @DisplayName("Should create mixed result")
        void shouldCreateMixedResult() {
            AssignmentResult result = AssignmentResult.mixed(
                    List.of("user1"),
                    List.of("group1"));
            assertEquals(AssignmentResult.ResultType.MIXED, result.getResultType());
            assertTrue(result.isSuccessful());
            assertTrue(result.hasAssignees());
            assertTrue(result.hasCandidateGroups());
        }

        @Test
        @DisplayName("Should create failed result")
        void shouldCreateFailedResult() {
            AssignmentResult result = AssignmentResult.failed("Assignment error");
            assertEquals(AssignmentResult.ResultType.FAILED, result.getResultType());
            assertFalse(result.isSuccessful());
            assertEquals("Assignment error", result.getErrorMessage());
        }

        @Test
        @DisplayName("Should create deferred result")
        void shouldCreateDeferredResult() {
            AssignmentResult result = AssignmentResult.deferred(
                    "All users busy",
                    java.time.Instant.now().plusSeconds(300));
            assertEquals(AssignmentResult.ResultType.DEFERRED, result.getResultType());
            assertFalse(result.isSuccessful());
            assertNotNull(result.getReassignAt());
        }
    }

    @Nested
    @DisplayName("Assignment Context Tests")
    class AssignmentContextTests {

        @Test
        @DisplayName("Should check if user is excluded")
        void shouldCheckIfUserIsExcluded() {
            AssignmentStrategyConfig config = AssignmentStrategyConfig.builder()
                    .excludeInitiator(true)
                    .build();

            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .strategyConfig(config)
                    .workflowInitiator("initiator")
                    .excludedUsers(List.of("blocked"))
                    .build();

            assertTrue(context.isExcluded("initiator"));
            assertTrue(context.isExcluded("blocked"));
            assertFalse(context.isExcluded("regular"));
        }

        @Test
        @DisplayName("Should check if user is candidate")
        void shouldCheckIfUserIsCandidate() {
            AssignmentContext context = AssignmentContext.builder()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .candidateUsers(List.of("candidate1", "candidate2"))
                    .build();

            assertTrue(context.isCandidate("candidate1"));
            assertFalse(context.isCandidate("other"));
        }

        @Test
        @DisplayName("Should create with defaults")
        void shouldCreateWithDefaults() {
            AssignmentContext context = AssignmentContext.withDefaults()
                    .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                    .build();

            assertEquals(5, context.getPriority());
            assertNotNull(context.getCandidateUsers());
            assertNotNull(context.getCandidateGroups());
            assertNotNull(context.getTaskData());
        }
    }
}
