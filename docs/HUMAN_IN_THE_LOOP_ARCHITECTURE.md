# Human-in-the-Loop Workflow Architecture

## Overview

This document describes the architecture for Human-in-the-Loop (HITL) workflow capabilities in the Lyshra OpenApp workflow engine. The implementation enables workflows to pause execution and wait for manual input, approvals, or decisions before continuing.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           External Systems                                   │
│  (UI, API, Email, Slack, etc.)                                              │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
                                  │ Signals (Approve, Reject, Complete, etc.)
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Signal Service                                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│  │ Approval Handler│  │Rejection Handler│  │ Timeout Handler │             │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘             │
└───────────┼─────────────────────┼──────────────────┼────────────────────────┘
            │                     │                   │
            └─────────────────────┼───────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Workflow Resumption Service                               │
│  • Reconstructs workflow context                                            │
│  • Determines next step from signal/branch                                   │
│  • Resumes workflow execution                                               │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
            ┌─────────────────────┼─────────────────────┐
            │                     │                     │
            ▼                     ▼                     ▼
┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐
│ Workflow Instance │  │   Human Task      │  │    Timeout        │
│   Repository      │  │   Repository      │  │    Scheduler      │
└───────────────────┘  └───────────────────┘  └───────────────────┘
            │                     │                     │
            └─────────────────────┼─────────────────────┘
                                  │
                          Persistent Storage
                     (MongoDB, PostgreSQL, etc.)
```

## Key Components

### 1. Workflow Execution States

The workflow execution state machine has been extended to support suspension:

```
                    ┌──────────────┐
                    │   RUNNING    │
                    └──────┬───────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│    WAITING    │  │    PAUSED     │  │   COMPLETED   │
│(Human Task)   │  │(Admin Pause)  │  │               │
└───────┬───────┘  └───────┬───────┘  └───────────────┘
        │                  │
        │  Signal          │  Signal
        │  Received        │  Received
        │                  │
        └──────────────────┘
                │
        ┌───────┼───────┐
        │       │       │
        ▼       ▼       ▼
    RUNNING  FAILED  TIMED_OUT
```

**State Definitions:**
- `RUNNING`: Workflow is actively executing
- `WAITING`: Workflow suspended at a human task step
- `PAUSED`: Workflow administratively paused
- `COMPLETED`: Workflow finished successfully
- `FAILED`: Workflow terminated due to error
- `TIMED_OUT`: Human task timed out without action
- `CANCELLED`: Workflow was cancelled

### 2. Human Task States

Human tasks have their own lifecycle:

```
    ┌──────────┐
    │ CREATED  │
    └────┬─────┘
         │
         ▼
    ┌──────────┐     ┌───────────┐
    │ PENDING  │────▶│ ASSIGNED  │
    └────┬─────┘     └─────┬─────┘
         │                 │
         ▼                 ▼
    ┌──────────────────────────┐
    │      IN_PROGRESS         │
    │    (Task Claimed)        │
    └──────────┬───────────────┘
               │
    ┌──────────┼──────────┬──────────────┐
    │          │          │              │
    ▼          ▼          ▼              ▼
┌────────┐ ┌────────┐ ┌─────────┐ ┌──────────┐
│APPROVED│ │REJECTED│ │COMPLETED│ │TIMED_OUT │
└────────┘ └────────┘ └─────────┘ └──────────┘
```

### 3. Signal Types

Signals are the mechanism for external interaction with waiting workflows:

| Signal Type | Purpose | Target |
|-------------|---------|--------|
| APPROVE | Approve a human task | Task |
| REJECT | Reject a human task | Task |
| COMPLETE | Complete with form data | Task |
| TIMEOUT | Indicate timeout occurred | Task/Workflow |
| ESCALATE | Trigger escalation | Task |
| CANCEL | Cancel task/workflow | Task/Workflow |
| PAUSE | Administratively pause | Workflow |
| RESUME | Resume paused workflow | Workflow |

## Design Patterns Used

### 1. State Pattern
Used for managing workflow and task state transitions. Each state defines valid transitions and associated behavior.

**Location:** `LyshraOpenAppWorkflowExecutionState`, `LyshraOpenAppHumanTaskStatus`

### 2. Strategy Pattern
Human task processors implement different strategies for human interaction:
- Approval strategy (binary decision)
- Decision strategy (multi-option selection)
- Manual Input strategy (form submission)

**Location:** `ILyshraOpenAppHumanTaskProcessor` implementations

### 3. Command Pattern
Signals encapsulate requests to the workflow engine. They carry all information needed to process the request and can be queued, logged, and replayed.

**Location:** `ILyshraOpenAppSignal`, signal handlers

### 4. Chain of Responsibility
Signal handlers are chained by priority. Each handler checks if it can process the signal and either handles it or passes to the next handler.

**Location:** `LyshraOpenAppSignalServiceImpl`

### 5. Observer Pattern (Timer-based)
The timeout scheduler monitors registered timeouts and notifies the signal service when timeouts occur.

**Location:** `ILyshraOpenAppTimeoutScheduler`

### 6. Memento Pattern
Workflow instance state is captured and persisted, allowing workflows to be restored after suspension or system restart.

**Location:** `ILyshraOpenAppWorkflowInstance`, `ILyshraOpenAppWorkflowInstanceRepository`

### 7. Repository Pattern
Abstracts data access for workflow instances and human tasks, enabling different storage backends.

**Location:** `ILyshraOpenAppWorkflowInstanceRepository`, `ILyshraOpenAppHumanTaskRepository`

### 8. Facade Pattern
Signal service provides a simplified interface to the complex signal processing subsystem.

**Location:** `ILyshraOpenAppSignalService`

## Package Structure

```
lyshra-open-app-plugin-contract/
└── src/main/java/com/lyshra/open/app/integration/
    ├── contract/
    │   ├── humantask/
    │   │   ├── ILyshraOpenAppHumanTask.java
    │   │   ├── ILyshraOpenAppHumanTaskProcessor.java
    │   │   ├── ILyshraOpenAppHumanTaskResult.java
    │   │   ├── ILyshraOpenAppHumanTaskRepository.java
    │   │   ├── ILyshraOpenAppHumanTaskEscalation.java
    │   │   ├── ILyshraOpenAppHumanTaskFormSchema.java
    │   │   ├── ILyshraOpenAppHumanTaskComment.java
    │   │   └── ILyshraOpenAppHumanTaskAuditEntry.java
    │   ├── signal/
    │   │   ├── ILyshraOpenAppSignal.java
    │   │   ├── ILyshraOpenAppSignalHandler.java
    │   │   └── ILyshraOpenAppSignalResult.java
    │   └── workflow/
    │       ├── ILyshraOpenAppWorkflowInstance.java
    │       └── ILyshraOpenAppWorkflowInstanceRepository.java
    └── enumerations/
        ├── LyshraOpenAppWorkflowExecutionState.java
        ├── LyshraOpenAppHumanTaskStatus.java
        ├── LyshraOpenAppHumanTaskType.java
        └── LyshraOpenAppSignalType.java

lyshra-open-app-core-engine/
└── src/main/java/com/lyshra/open/app/core/engine/
    ├── signal/
    │   ├── ILyshraOpenAppSignalService.java
    │   └── impl/
    │       ├── LyshraOpenAppSignalServiceImpl.java
    │       ├── LyshraOpenAppSignal.java
    │       ├── LyshraOpenAppSignalResult.java
    │       ├── HumanTaskApprovalSignalHandler.java
    │       ├── HumanTaskRejectionSignalHandler.java
    │       ├── HumanTaskCompletionSignalHandler.java
    │       ├── WorkflowPauseResumeSignalHandler.java
    │       └── TimeoutSignalHandler.java
    ├── timeout/
    │   ├── ILyshraOpenAppTimeoutScheduler.java
    │   └── impl/
    │       └── LyshraOpenAppTimeoutSchedulerImpl.java
    ├── escalation/
    │   ├── ILyshraOpenAppEscalationService.java
    │   └── impl/
    │       └── LyshraOpenAppEscalationServiceImpl.java
    ├── humantask/
    │   ├── ILyshraOpenAppHumanTaskService.java
    │   └── impl/
    │       └── LyshraOpenAppHumanTaskServiceImpl.java
    └── node/
        ├── ILyshraOpenAppWorkflowResumptionService.java
        └── impl/
            └── LyshraOpenAppWorkflowResumptionServiceImpl.java

lyshra-open-app-plugin-contract-model/
└── src/main/java/com/lyshra/open/app/integration/models/
    └── humantask/
        ├── LyshraOpenAppHumanTaskProcessorDefinition.java
        ├── LyshraOpenAppHumanTaskResult.java
        └── DefaultHumanTaskConfigBuilder.java

lyshra-open-app-plugins/lyshra-open-app-core-processors-plugin/
└── src/main/java/.../processors/humantask/
    ├── ApprovalProcessor.java
    ├── ManualInputProcessor.java
    └── DecisionProcessor.java
```

## Timeout and Escalation Handling

### Timeout Configuration

Each human task can have a timeout configuration:

```java
ILyshraOpenAppHumanTaskConfig config = taskConfigBuilder
    .title("Approve Request")
    .assignees(List.of("manager@company.com"))
    .timeout(Duration.ofHours(24))
    .escalation(escalationConfig)
    .build();
```

### Escalation Flow

```
┌─────────────────┐
│  Task Created   │
└────────┬────────┘
         │
         │ Timeout (Level 1)
         ▼
┌─────────────────┐     ┌─────────────────────────────┐
│   Escalation    │────▶│ Action: REASSIGN/NOTIFY/etc │
│   Level 1       │     └─────────────────────────────┘
└────────┬────────┘
         │
         │ Timeout (Level 2)
         ▼
┌─────────────────┐     ┌─────────────────────────────┐
│   Escalation    │────▶│ Action: Add more assignees   │
│   Level 2       │     └─────────────────────────────┘
└────────┬────────┘
         │
         │ Final Timeout
         ▼
┌─────────────────────────────────────────────────────┐
│ Final Action: AUTO_APPROVE / AUTO_REJECT / FAIL     │
└─────────────────────────────────────────────────────┘
```

### Escalation Actions

| Action | Description |
|--------|-------------|
| REASSIGN | Replace assignees with escalation targets |
| ADD_ASSIGNEES | Add escalation targets to existing assignees |
| NOTIFY | Send notifications without changing assignment |
| CUSTOM_HANDLER | Execute custom escalation logic |
| AUTO_APPROVE | Automatically approve the task |
| AUTO_REJECT | Automatically reject the task |
| FAIL_WORKFLOW | Fail the workflow |
| SKIP_TASK | Skip the task and continue |

## Workflow Definition Example

```yaml
workflow:
  name: "purchase-order-approval"
  startStep: "validate-order"
  steps:
    validate-order:
      type: PROCESSOR
      processor: "core:core-processors:1.0:JAVASCRIPT_PROCESSOR"
      inputConfig:
        script: "result = $data.amount > 0"
      next:
        branches:
          true: "check-amount"
          false: "reject-invalid"

    check-amount:
      type: PROCESSOR
      processor: "core:core-processors:1.0:IF_PROCESSOR"
      inputConfig:
        expression: "$data.amount > 10000"
      next:
        branches:
          true: "manager-approval"
          false: "auto-approve"

    manager-approval:
      type: PROCESSOR
      processor: "core:core-processors:1.0:APPROVAL_PROCESSOR"
      inputConfig:
        title: "Approve Large Purchase Order"
        description: "PO amount exceeds $10,000"
        assignees: []
        candidateGroups: ["managers"]
        timeout: "PT48H"
        autoRejectOnTimeout: true
      next:
        branches:
          APPROVED: "process-order"
          REJECTED: "notify-rejection"
          TIMED_OUT: "escalate-timeout"

    process-order:
      type: PROCESSOR
      processor: "core:core-processors:1.0:API_PROCESSOR"
      # ... process the order
```

## API Integration Points

### 1. Creating a Human Task (Internal)

Human task processors use `ILyshraOpenAppHumanTaskService`:

```java
humanTaskService.createTask(
    workflowInstanceId,
    workflowStepId,
    LyshraOpenAppHumanTaskType.APPROVAL,
    taskConfig,
    context
).subscribe();
```

### 2. Resolving a Human Task (External)

External systems use `ILyshraOpenAppSignalService`:

```java
// Approve a task
signalService.approve(taskId, userId, additionalData)
    .subscribe(result -> {
        if (result.isSuccess()) {
            // Task approved, workflow resumed
        }
    });

// Reject a task
signalService.reject(taskId, userId, "Insufficient budget")
    .subscribe();

// Complete with form data
signalService.complete(taskId, userId, formData)
    .subscribe();
```

### 3. Querying Tasks (External)

External systems use `ILyshraOpenAppHumanTaskRepository`:

```java
// Find tasks for a user
taskRepository.findByAssignee(userId, List.of(PENDING, IN_PROGRESS))
    .collectList()
    .subscribe(tasks -> {
        // Display tasks to user
    });

// Find tasks by candidate groups
taskRepository.findByCandidateGroups(userGroups, List.of(PENDING))
    .collectList()
    .subscribe(tasks -> {
        // Display available tasks
    });
```

## Future Extensions

### 1. Multi-Step Approvals

Support for sequential or parallel multi-level approvals:

```java
public interface ILyshraOpenAppMultiLevelApproval {
    List<ApprovalLevel> getLevels();
    ApprovalStrategy getStrategy(); // SEQUENTIAL, PARALLEL, ANY_N_OF_M
}
```

### 2. Audit Tracking Enhancements

Extended audit capabilities:
- Full context snapshots at each state change
- Integration with external audit systems
- Compliance reporting
- SLA tracking

### 3. Notification Integration

Pluggable notification system:
- Email notifications
- Slack/Teams integration
- SMS notifications
- Webhook callbacks

### 4. Task Inbox Features

UI-focused enhancements:
- Task filtering and sorting
- Bulk operations
- Task delegation workflows
- Saved views

### 5. Analytics and Reporting

- Task completion time metrics
- Bottleneck identification
- SLA compliance reporting
- User productivity metrics

## Best Practices

### 1. Always Set Timeouts

Never leave human tasks without timeouts in production:

```java
.timeout(Duration.ofHours(24))
.escalation(escalationConfig)
```

### 2. Use Candidate Groups

Prefer candidate groups over specific assignees for flexibility:

```java
.candidateGroups(List.of("approvers", "managers"))
```

### 3. Include Context in Task Data

Provide enough information for users to make decisions:

```java
.taskData(Map.of(
    "orderDetails", context.getData(),
    "customerHistory", getCustomerHistory(customerId)
))
```

### 4. Handle All Outcomes

Always define next steps for all possible outcomes:

```yaml
next:
  branches:
    APPROVED: "process"
    REJECTED: "notify-rejection"
    TIMED_OUT: "escalate"
```

### 5. Use Idempotent Signal Processing

Design signal handlers to be idempotent - processing the same signal twice should be safe.

## Conclusion

The Human-in-the-Loop architecture provides a robust foundation for building workflows that require human intervention. The design emphasizes:

- **Clean separation of concerns** between workflow execution and human task management
- **Extensibility** through plugins and configuration
- **Reliability** through state persistence and timeout handling
- **Flexibility** through multiple task types and escalation strategies

The implementation follows enterprise-grade patterns and is ready for production use in regulated environments.
