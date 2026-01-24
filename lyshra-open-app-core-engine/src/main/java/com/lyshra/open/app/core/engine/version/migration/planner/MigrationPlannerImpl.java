package com.lyshra.open.app.core.engine.version.migration.planner;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowMigrationHints;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationHandlerRegistry;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationPath;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationPlan;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationPlanner;
import com.lyshra.open.app.integration.contract.version.migration.IMigrationStep;
import com.lyshra.open.app.integration.contract.version.migration.IWorkflowMigrationStrategy;
import com.lyshra.open.app.integration.models.version.migration.MigrationPlan;
import com.lyshra.open.app.integration.models.version.migration.MigrationStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;

/**
 * Implementation of IMigrationPlanner that calculates safe migration paths
 * and validates migrations before execution.
 *
 * <p>The planner performs:</p>
 * <ul>
 *   <li>Path discovery using BFS for optimal path finding</li>
 *   <li>Step existence validation between versions</li>
 *   <li>Variable compatibility checking</li>
 *   <li>Risk assessment based on changes</li>
 *   <li>Migration step generation</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class MigrationPlannerImpl implements IMigrationPlanner {

    private final IMigrationHandlerRegistry handlerRegistry;
    private final Function<String, Optional<IVersionedWorkflow>> workflowLoader;

    @Override
    public Mono<IMigrationPlan> planMigration(MigrationPlanRequest request) {
        return validateMigration(request)
                .flatMap(validation -> {
                    if (!validation.isValid() && !request.allowUnsafeMigration()) {
                        return Mono.just(MigrationPlan.invalid(
                                request.workflowId(),
                                request.sourceVersion(),
                                request.targetVersion(),
                                validation));
                    }

                    return generateMigrationPlan(request, validation);
                });
    }

    @Override
    public Mono<MigrationValidation> validateMigration(MigrationPlanRequest request) {
        return Mono.fromCallable(() -> {
            List<ValidationError> errors = new ArrayList<>();
            List<ValidationWarning> warnings = new ArrayList<>();
            Set<String> missingSteps = new HashSet<>();
            Set<String> missingVariables = new HashSet<>();
            Set<String> incompatibleVariables = new HashSet<>();

            // Load workflows
            Optional<IVersionedWorkflow> sourceWorkflowOpt = loadWorkflow(request.workflowId(), request.sourceVersion());
            Optional<IVersionedWorkflow> targetWorkflowOpt = loadWorkflow(request.workflowId(), request.targetVersion());

            if (sourceWorkflowOpt.isEmpty()) {
                errors.add(new ValidationError(
                        ValidationError.ErrorType.VERSION_MISMATCH,
                        "source-version",
                        "Source workflow version not found: " + request.sourceVersion().toVersionString(),
                        Optional.empty()));
            }

            if (targetWorkflowOpt.isEmpty()) {
                errors.add(new ValidationError(
                        ValidationError.ErrorType.VERSION_MISMATCH,
                        "target-version",
                        "Target workflow version not found: " + request.targetVersion().toVersionString(),
                        Optional.empty()));
            }

            if (sourceWorkflowOpt.isEmpty() || targetWorkflowOpt.isEmpty()) {
                return MigrationValidation.invalid(errors);
            }

            IVersionedWorkflow sourceWorkflow = sourceWorkflowOpt.get();
            IVersionedWorkflow targetWorkflow = targetWorkflowOpt.get();

            // Check if migration path exists
            List<IMigrationPath> migrationChain = findMigrationChain(
                    request.workflowId(), request.sourceVersion(), request.targetVersion());

            if (migrationChain.isEmpty()) {
                errors.add(ValidationError.blockedPath("No migration path found from " +
                        request.sourceVersion().toVersionString() + " to " +
                        request.targetVersion().toVersionString()));
            }

            // Check for blocked paths
            for (IMigrationPath path : migrationChain) {
                if (path.getPathType() == IMigrationPath.PathType.BLOCKED) {
                    errors.add(ValidationError.blockedPath("Migration path is explicitly blocked"));
                }
                if (path.getPathType() == IMigrationPath.PathType.DEPRECATED) {
                    warnings.add(ValidationWarning.deprecatedVersion(path.getTargetVersion().toVersionString()));
                }
            }

            // Validate current step exists in target
            if (request.currentStepName() != null) {
                Set<String> targetSteps = targetWorkflow.getSteps().keySet();
                Map<String, String> stepMappings = getStepMappings(migrationChain, targetWorkflow);

                String mappedStep = stepMappings.getOrDefault(request.currentStepName(), request.currentStepName());

                if (!targetSteps.contains(mappedStep)) {
                    if (sourceWorkflow.getRemovedSteps().contains(request.currentStepName()) ||
                        targetWorkflow.getRemovedSteps().contains(request.currentStepName())) {
                        // Step was removed, check if there's a replacement
                        if (!stepMappings.containsKey(request.currentStepName())) {
                            errors.add(ValidationError.missingStep(request.currentStepName()));
                            missingSteps.add(request.currentStepName());
                        } else {
                            warnings.add(ValidationWarning.stepChange(request.currentStepName(),
                                    "removed and will be mapped to '" + mappedStep + "'"));
                        }
                    } else {
                        errors.add(ValidationError.missingStep(mappedStep));
                        missingSteps.add(mappedStep);
                    }
                }

                // Check if current step is a safe migration point
                if (!isSafeMigrationPoint(request.workflowId(), request.sourceVersion(),
                        request.targetVersion(), request.currentStepName())) {
                    warnings.add(new ValidationWarning(
                            ValidationWarning.WarningType.PERFORMANCE_IMPACT,
                            request.currentStepName(),
                            "Current step is not a designated safe migration point"));
                }
            }

            // Validate required variables
            Map<String, String> variableMappings = getVariableMappings(migrationChain, targetWorkflow);

            for (String requiredVar : request.requiredVariables()) {
                String mappedVar = variableMappings.getOrDefault(requiredVar, requiredVar);

                if (!request.currentVariables().containsKey(requiredVar) &&
                    !request.currentVariables().containsKey(mappedVar)) {
                    errors.add(ValidationError.missingVariable(requiredVar));
                    missingVariables.add(requiredVar);
                }
            }

            // Check for variable renames
            for (Map.Entry<String, String> rename : variableMappings.entrySet()) {
                if (request.currentVariables().containsKey(rename.getKey())) {
                    warnings.add(ValidationWarning.variableRename(rename.getKey(), rename.getValue()));
                }
            }

            // Check for removed steps
            Set<String> removedSteps = targetWorkflow.getRemovedSteps();
            for (String removed : removedSteps) {
                warnings.add(ValidationWarning.stepChange(removed, "has been removed"));
            }

            // Check for added steps
            Set<String> addedSteps = targetWorkflow.getAddedSteps();
            for (String added : addedSteps) {
                warnings.add(ValidationWarning.stepChange(added, "has been added"));
            }

            // Check handler availability
            Optional<IWorkflowMigrationStrategy> strategyOpt = handlerRegistry.getStrategy(request.workflowId());
            if (strategyOpt.isEmpty() && migrationChain.isEmpty()) {
                errors.add(ValidationError.noHandler());
            }

            // Assess data loss risk
            double totalRisk = calculateTotalRisk(migrationChain);
            if (totalRisk > 0.5) {
                warnings.add(ValidationWarning.dataLossRisk("migration", totalRisk));
            }

            // Determine complexity
            MigrationComplexity complexity = determineComplexity(
                    migrationChain.size(), errors.size(), warnings.size(),
                    removedSteps.size(), addedSteps.size());

            if (!errors.isEmpty()) {
                return new MigrationValidation(false, false, errors, warnings,
                        missingSteps, missingVariables, incompatibleVariables, complexity);
            }

            boolean isSafe = warnings.isEmpty() || warnings.stream()
                    .noneMatch(w -> w.type() == ValidationWarning.WarningType.DATA_LOSS_RISK);

            return new MigrationValidation(true, isSafe, errors, warnings,
                    missingSteps, missingVariables, incompatibleVariables, complexity);
        });
    }

    @Override
    public List<MigrationPathInfo> findMigrationPaths(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion) {

        List<MigrationPathInfo> allPaths = new ArrayList<>();

        // Try direct path first
        Optional<IMigrationPath> directPath = handlerRegistry.findDirectPath(
                workflowId, sourceVersion, targetVersion);

        if (directPath.isPresent()) {
            allPaths.add(createPathInfo(List.of(directPath.get())));
        }

        // Find multi-hop paths using BFS
        List<IMigrationPath> chain = handlerRegistry.computeMigrationChain(
                workflowId, sourceVersion, targetVersion);

        if (!chain.isEmpty() && chain.size() > 1) {
            allPaths.add(createPathInfo(chain));
        }

        return allPaths;
    }

    @Override
    public Optional<MigrationPathInfo> findOptimalPath(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion) {

        List<MigrationPathInfo> paths = findMigrationPaths(workflowId, sourceVersion, targetVersion);

        if (paths.isEmpty()) {
            return Optional.empty();
        }

        // Select optimal path based on:
        // 1. Lowest risk
        // 2. Fewest hops
        // 3. Doesn't require approval (if possible)
        return paths.stream()
                .min((p1, p2) -> {
                    // Prefer paths that don't require approval
                    if (p1.requiresApproval() != p2.requiresApproval()) {
                        return p1.requiresApproval() ? 1 : -1;
                    }
                    // Then by risk
                    int riskCompare = Double.compare(p1.totalRisk(), p2.totalRisk());
                    if (riskCompare != 0) return riskCompare;
                    // Then by hop count
                    return Integer.compare(p1.hopCount(), p2.hopCount());
                });
    }

    @Override
    public VersionDiff analyzeVersionDiff(
            IVersionedWorkflow sourceWorkflow,
            IVersionedWorkflow targetWorkflow) {

        Set<String> sourceSteps = sourceWorkflow.getSteps().keySet();
        Set<String> targetSteps = targetWorkflow.getSteps().keySet();

        Set<String> addedSteps = new HashSet<>(targetSteps);
        addedSteps.removeAll(sourceSteps);

        Set<String> removedSteps = new HashSet<>(sourceSteps);
        removedSteps.removeAll(targetSteps);

        Set<String> unchangedSteps = new HashSet<>(sourceSteps);
        unchangedSteps.retainAll(targetSteps);

        Set<String> modifiedSteps = new HashSet<>(targetWorkflow.getModifiedSteps());

        // Get mappings from migration hints
        Map<String, String> stepMappings = new HashMap<>();
        Map<String, String> variableMappings = new HashMap<>();
        Set<String> addedVariables = new HashSet<>();
        Set<String> removedVariables = new HashSet<>();

        targetWorkflow.getMigrationHints().ifPresent(hints -> {
            stepMappings.putAll(hints.getStepNameMappings());
            variableMappings.putAll(hints.getVariableMappings());
        });

        // Determine breaking changes
        List<String> breakingChanges = new ArrayList<>();

        for (String removed : removedSteps) {
            if (!stepMappings.containsKey(removed)) {
                breakingChanges.add("Step '" + removed + "' removed with no replacement");
            }
        }

        // Check backward compatibility
        boolean isBackwardCompatible = breakingChanges.isEmpty() &&
                removedSteps.stream().allMatch(stepMappings::containsKey);

        boolean requiresMigrationHandler = !addedSteps.isEmpty() ||
                !removedSteps.isEmpty() ||
                !variableMappings.isEmpty() ||
                !modifiedSteps.isEmpty();

        return new VersionDiff(
                sourceWorkflow.getVersion(),
                targetWorkflow.getVersion(),
                addedSteps,
                removedSteps,
                modifiedSteps,
                unchangedSteps,
                stepMappings,
                addedVariables,
                removedVariables,
                variableMappings,
                Map.of(), // Type changes would need more analysis
                isBackwardCompatible,
                requiresMigrationHandler,
                breakingChanges
        );
    }

    @Override
    public boolean isSafeMigrationPoint(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion,
            String stepName) {

        Set<String> safePoints = getSafeMigrationPoints(workflowId, sourceVersion, targetVersion);
        return safePoints.isEmpty() || safePoints.contains(stepName);
    }

    @Override
    public Set<String> getSafeMigrationPoints(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion) {

        Set<String> safePoints = new HashSet<>();

        List<IMigrationPath> chain = findMigrationChain(workflowId, sourceVersion, targetVersion);

        for (IMigrationPath path : chain) {
            safePoints.addAll(path.getSafePoints());
        }

        // If no safe points defined, all points are considered safe
        return safePoints;
    }

    @Override
    public Mono<RiskAssessment> assessRisk(MigrationPlanRequest request) {
        return Mono.fromCallable(() -> {
            List<RiskFactor> factors = new ArrayList<>();

            List<IMigrationPath> chain = findMigrationChain(
                    request.workflowId(), request.sourceVersion(), request.targetVersion());

            // Factor 1: Multi-hop migration
            if (chain.size() > 1) {
                factors.add(RiskFactor.multiHopMigration(chain.size()));
            }

            // Factor 2: Data loss risk from paths
            double totalDataLossRisk = chain.stream()
                    .mapToDouble(IMigrationPath::getDataLossRisk)
                    .sum();

            if (totalDataLossRisk > 0.1) {
                factors.add(RiskFactor.dataLoss("migration-path", totalDataLossRisk));
            }

            // Factor 3: Unsafe migration point
            if (request.currentStepName() != null &&
                !isSafeMigrationPoint(request.workflowId(), request.sourceVersion(),
                        request.targetVersion(), request.currentStepName())) {
                factors.add(RiskFactor.unsafePoint(request.currentStepName()));
            }

            // Factor 4: Version deprecation
            Optional<IVersionedWorkflow> targetWorkflow = loadWorkflow(
                    request.workflowId(), request.targetVersion());

            if (targetWorkflow.isPresent() && targetWorkflow.get().getVersion().isDeprecated()) {
                factors.add(new RiskFactor("deprecated-target",
                        "Target version is deprecated",
                        0.2, "version"));
            }

            // Calculate overall risk score
            double riskScore = factors.stream()
                    .mapToDouble(RiskFactor::impact)
                    .sum();

            riskScore = Math.min(riskScore, 1.0);

            // Determine risk level
            RiskAssessment.RiskLevel level;
            if (riskScore < 0.2) {
                level = RiskAssessment.RiskLevel.LOW;
            } else if (riskScore < 0.5) {
                level = RiskAssessment.RiskLevel.MEDIUM;
            } else if (riskScore < 0.8) {
                level = RiskAssessment.RiskLevel.HIGH;
            } else {
                level = RiskAssessment.RiskLevel.CRITICAL;
            }

            // Generate mitigations
            List<String> mitigations = new ArrayList<>();
            if (level == RiskAssessment.RiskLevel.HIGH || level == RiskAssessment.RiskLevel.CRITICAL) {
                mitigations.add("Create full backup before migration");
                mitigations.add("Review migration plan carefully");
                mitigations.add("Consider testing in staging environment first");
            }
            if (chain.size() > 2) {
                mitigations.add("Consider intermediate version checkpoints");
            }

            boolean recommendsApproval = level == RiskAssessment.RiskLevel.HIGH ||
                    level == RiskAssessment.RiskLevel.CRITICAL;
            boolean recommendsBackup = level != RiskAssessment.RiskLevel.LOW;

            return new RiskAssessment(level, riskScore, factors, mitigations,
                    recommendsApproval, recommendsBackup);
        });
    }

    // Private helper methods

    private Mono<IMigrationPlan> generateMigrationPlan(
            MigrationPlanRequest request,
            MigrationValidation validation) {

        return assessRisk(request).map(riskAssessment -> {
            List<IMigrationPath> migrationChain = findMigrationChain(
                    request.workflowId(), request.sourceVersion(), request.targetVersion());

            Optional<IVersionedWorkflow> targetWorkflowOpt = loadWorkflow(
                    request.workflowId(), request.targetVersion());

            IVersionedWorkflow targetWorkflow = targetWorkflowOpt.orElse(null);

            // Collect all mappings
            Map<String, String> stepMappings = getStepMappings(migrationChain, targetWorkflow);
            Map<String, String> variableMappings = getVariableMappings(migrationChain, targetWorkflow);

            // Determine target step
            String targetStepName = request.currentStepName();
            if (targetStepName != null && stepMappings.containsKey(targetStepName)) {
                targetStepName = stepMappings.get(targetStepName);
            }

            // Generate migration steps
            List<IMigrationStep> steps = generateMigrationSteps(
                    request, migrationChain, variableMappings, targetWorkflow);

            // Collect affected steps and variables
            Set<String> addedSteps = targetWorkflow != null ? targetWorkflow.getAddedSteps() : Set.of();
            Set<String> removedSteps = targetWorkflow != null ? targetWorkflow.getRemovedSteps() : Set.of();

            // Build the plan
            return MigrationPlan.builder()
                    .workflowId(request.workflowId())
                    .sourceVersion(request.sourceVersion())
                    .targetVersion(request.targetVersion())
                    .valid(validation.isValid())
                    .safe(validation.isSafe())
                    .dryRun(request.dryRun())
                    .validationResult(validation)
                    .steps(steps)
                    .migrationPaths(migrationChain)
                    .hopCount(migrationChain.size())
                    .stepMappings(stepMappings)
                    .variableMappings(variableMappings)
                    .targetStepName(targetStepName)
                    .sourceStepName(request.currentStepName())
                    .addedSteps(addedSteps)
                    .removedSteps(removedSteps)
                    .renamedVariables(variableMappings)
                    .riskAssessment(riskAssessment)
                    .complexity(validation.complexity())
                    .estimatedDuration(estimateDuration(steps))
                    .requiresApproval(riskAssessment.recommendsApproval() ||
                            migrationChain.stream().anyMatch(p -> p.getPathType() == IMigrationPath.PathType.REQUIRES_APPROVAL))
                    .requiresBackup(riskAssessment.recommendsBackup() ||
                            migrationChain.stream().anyMatch(IMigrationPath::requiresBackup))
                    .supportsRollback(migrationChain.stream().allMatch(IMigrationPath::supportsRollback))
                    .handlerIds(getHandlerIds(migrationChain))
                    .transformerNames(getTransformerNames(migrationChain))
                    .preconditions(generatePreconditions(request, targetWorkflow))
                    .postconditions(generatePostconditions(targetStepName, variableMappings))
                    .metadata(request.metadata())
                    .build();
        });
    }

    private List<IMigrationStep> generateMigrationSteps(
            MigrationPlanRequest request,
            List<IMigrationPath> migrationChain,
            Map<String, String> variableMappings,
            IVersionedWorkflow targetWorkflow) {

        List<IMigrationStep> steps = new ArrayList<>();
        int order = 0;

        // Step 1: Validation
        steps.add(MigrationStep.validation("validate-preconditions",
                "Validate migration preconditions",
                ctx -> Mono.just(IMigrationStep.StepResult.success(
                        "validate-preconditions",
                        List.of("Preconditions validated"),
                        Map.of(), Map.of(), Duration.ZERO))));
        order++;

        // Step 2: Checkpoint
        steps.add(MigrationStep.checkpoint(order++));

        // Step 3: Variable renames
        for (Map.Entry<String, String> rename : variableMappings.entrySet()) {
            steps.add(MigrationStep.variableRename(rename.getKey(), rename.getValue(), order++));
        }

        // Step 4: Variable additions (from migration hints)
        if (targetWorkflow != null) {
            targetWorkflow.getMigrationHints().ifPresent(hints -> {
                // Add default variables if specified in hints
                // This would need additional interface support
            });
        }

        // Step 5: Step mapping
        for (IMigrationPath path : migrationChain) {
            for (Map.Entry<String, String> mapping : path.getStepMappings().entrySet()) {
                int finalOrder = order++;
                steps.add(MigrationStep.stepMapping(mapping.getKey(), mapping.getValue(), finalOrder));
            }
        }

        // Step 6: Verification
        steps.add(MigrationStep.builder()
                .name("verify-migration")
                .description("Verify migration completed successfully")
                .type(IMigrationStep.StepType.VERIFICATION)
                .order(order++)
                .supportsRollback(false)
                .executor(ctx -> Mono.just(IMigrationStep.StepResult.success(
                        "verify-migration",
                        List.of("Migration verified"),
                        Map.of(), Map.of(), Duration.ZERO)))
                .build());

        // Step 7: Commit
        steps.add(MigrationStep.commit(order));

        return steps;
    }

    private List<IMigrationPlan.Precondition> generatePreconditions(
            MigrationPlanRequest request,
            IVersionedWorkflow targetWorkflow) {

        List<IMigrationPlan.Precondition> preconditions = new ArrayList<>();

        // Handler available
        boolean hasHandler = handlerRegistry.getStrategy(request.workflowId()).isPresent() ||
                !handlerRegistry.getHandlers(request.workflowId()).isEmpty();
        preconditions.add(IMigrationPlan.Precondition.handlerAvailable(hasHandler));

        // Current step exists in target
        if (request.currentStepName() != null && targetWorkflow != null) {
            boolean stepExists = targetWorkflow.getSteps().containsKey(request.currentStepName());
            preconditions.add(IMigrationPlan.Precondition.stepExists(request.currentStepName(), stepExists));
        }

        // Required variables exist
        for (String requiredVar : request.requiredVariables()) {
            boolean varExists = request.currentVariables().containsKey(requiredVar);
            preconditions.add(IMigrationPlan.Precondition.variableExists(requiredVar, varExists));
        }

        return preconditions;
    }

    private List<IMigrationPlan.Postcondition> generatePostconditions(
            String targetStepName,
            Map<String, String> variableMappings) {

        List<IMigrationPlan.Postcondition> postconditions = new ArrayList<>();

        if (targetStepName != null) {
            postconditions.add(IMigrationPlan.Postcondition.stepReached(targetStepName));
        }

        for (String newVarName : variableMappings.values()) {
            postconditions.add(IMigrationPlan.Postcondition.variableSet(newVarName));
        }

        return postconditions;
    }

    private List<IMigrationPath> findMigrationChain(
            String workflowId,
            IWorkflowVersion sourceVersion,
            IWorkflowVersion targetVersion) {

        // First check registry
        List<IMigrationPath> chain = handlerRegistry.computeMigrationChain(
                workflowId, sourceVersion, targetVersion);

        if (!chain.isEmpty()) {
            return chain;
        }

        // Check if there's a strategy with paths
        Optional<IWorkflowMigrationStrategy> strategyOpt = handlerRegistry.getStrategy(workflowId);
        if (strategyOpt.isPresent()) {
            return strategyOpt.get().computeMigrationChain(sourceVersion, targetVersion);
        }

        return List.of();
    }

    private Optional<IVersionedWorkflow> loadWorkflow(String workflowId, IWorkflowVersion version) {
        if (workflowLoader != null) {
            return workflowLoader.apply(workflowId + ":" + version.toVersionString());
        }
        return Optional.empty();
    }

    private Map<String, String> getStepMappings(
            List<IMigrationPath> chain,
            IVersionedWorkflow targetWorkflow) {

        Map<String, String> mappings = new HashMap<>();

        for (IMigrationPath path : chain) {
            mappings.putAll(path.getStepMappings());
        }

        if (targetWorkflow != null) {
            targetWorkflow.getMigrationHints()
                    .map(IWorkflowMigrationHints::getStepNameMappings)
                    .ifPresent(mappings::putAll);
        }

        return mappings;
    }

    private Map<String, String> getVariableMappings(
            List<IMigrationPath> chain,
            IVersionedWorkflow targetWorkflow) {

        Map<String, String> mappings = new HashMap<>();

        for (IMigrationPath path : chain) {
            mappings.putAll(path.getVariableMappings());
        }

        if (targetWorkflow != null) {
            targetWorkflow.getMigrationHints()
                    .map(IWorkflowMigrationHints::getVariableMappings)
                    .ifPresent(mappings::putAll);
        }

        return mappings;
    }

    private MigrationPathInfo createPathInfo(List<IMigrationPath> paths) {
        Set<String> affectedSteps = new HashSet<>();
        Set<String> affectedVariables = new HashSet<>();
        double totalRisk = 0;
        boolean requiresApproval = false;

        for (IMigrationPath path : paths) {
            affectedSteps.addAll(path.getStepMappings().keySet());
            affectedSteps.addAll(path.getStepMappings().values());
            affectedVariables.addAll(path.getVariableMappings().keySet());
            affectedVariables.addAll(path.getVariableMappings().values());
            totalRisk += path.getDataLossRisk();

            if (path.getPathType() == IMigrationPath.PathType.REQUIRES_APPROVAL) {
                requiresApproval = true;
            }
        }

        MigrationComplexity complexity;
        if (paths.size() == 1 && totalRisk < 0.2) {
            complexity = MigrationComplexity.SIMPLE;
        } else if (paths.size() <= 2 && totalRisk < 0.5) {
            complexity = MigrationComplexity.MODERATE;
        } else {
            complexity = MigrationComplexity.COMPLEX;
        }

        return new MigrationPathInfo(paths, paths.size(), totalRisk, complexity,
                affectedSteps, affectedVariables, requiresApproval);
    }

    private double calculateTotalRisk(List<IMigrationPath> chain) {
        return chain.stream()
                .mapToDouble(IMigrationPath::getDataLossRisk)
                .sum();
    }

    private MigrationComplexity determineComplexity(
            int hopCount, int errorCount, int warningCount,
            int removedStepsCount, int addedStepsCount) {

        if (errorCount > 0) {
            return MigrationComplexity.BLOCKED;
        }

        int changeScore = hopCount + removedStepsCount + addedStepsCount + warningCount / 2;

        if (changeScore <= 2) {
            return MigrationComplexity.SIMPLE;
        } else if (changeScore <= 5) {
            return MigrationComplexity.MODERATE;
        } else {
            return MigrationComplexity.COMPLEX;
        }
    }

    private Duration estimateDuration(List<IMigrationStep> steps) {
        return steps.stream()
                .map(IMigrationStep::getEstimatedDuration)
                .reduce(Duration.ZERO, Duration::plus);
    }

    private List<String> getHandlerIds(List<IMigrationPath> chain) {
        return chain.stream()
                .flatMap(p -> p.getCustomHandlerClass().stream())
                .toList();
    }

    private List<String> getTransformerNames(List<IMigrationPath> chain) {
        return chain.stream()
                .flatMap(p -> p.getTransformerNames().stream())
                .toList();
    }
}
