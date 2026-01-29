# Lyshra Open App

Lyshra Open App is an extensible **Backend-as-Config** platform that enables applications to define their entire backend through configuration. It combines a workflow engine (similar to n8n) with flexible data object management (similar to IBM OpenPages), allowing millions of applications to register and operate without writing any code.

## Vision

The intention is to provide a platform where any backend application can be configured using just configuration files, enabling integration with:
- Custom workflows
- Multiple datasources (MySQL, MongoDB, etc.)
- Custom database objects with queries
- Various authentication mechanisms

## Tech Stack

- **Java 21**
- **Spring Boot 4.0.0**
- **Maven** (multi-module project)
- **GraalVM** (JavaScript expression evaluation)
- **Project Reactor** (reactive programming)

## Project Structure

```
lyshra-open-app/
├── lyshra-open-app-core-engine/          # Core engine implementation
├── lyshra-open-app-plugin-contract/      # Plugin interfaces/contracts
├── lyshra-open-app-plugin-contract-model/ # Model implementations
├── lyshra-open-app-plugins/              # Plugin implementations
│   ├── lyshra-open-app-core-processors-plugin/   # Core processors
│   └── lyshra-open-app-sample-workflow-plugin/   # Sample plugin
└── pom.xml                               # Parent POM
```

## Modules

### 1. lyshra-open-app-core-engine

The core engine that orchestrates everything:

| Component | Description |
|-----------|-------------|
| **Plugin Loader** | Loads plugins using Java SPI (ServiceLoader) |
| **Plugin Factory** | Creates and manages plugin instances |
| **Workflow Executor** | Executes workflow definitions |
| **Workflow Step Executor** | Executes individual workflow steps |
| **Processor Executor** | Executes processor logic |
| **Expression Evaluators** | GraalVM JavaScript & Spring SpEL evaluators |
| **Datasource Engine** | Manages datasource connections |
| **Error Handler** | Centralized error handling and resolution |

### 2. lyshra-open-app-plugin-contract

Defines the interfaces that all plugins must implement:

#### Core Interfaces

| Interface | Description |
|-----------|-------------|
| `ILyshraOpenAppPlugin` | Main plugin interface |
| `ILyshraOpenAppPluginProvider` | Factory interface for creating plugins (SPI entry point) |
| `ILyshraOpenAppPluginFacade` | Facade provided to plugins for accessing engine services |

#### Processor Interfaces

| Interface | Description |
|-----------|-------------|
| `ILyshraOpenAppProcessor` | Defines a reusable processor |
| `ILyshraOpenAppProcessorFunction` | The actual processing logic |
| `ILyshraOpenAppProcessorInputConfig` | Input configuration for processors |
| `ILyshraOpenAppProcessorResult` | Result returned by processors |

#### Workflow Interfaces

| Interface | Description |
|-----------|-------------|
| `ILyshraOpenAppWorkflow` | Defines a workflow |
| `ILyshraOpenAppWorkflowStep` | Defines a step within a workflow |
| `ILyshraOpenAppWorkflowStepNext` | Defines branching logic |
| `ILyshraOpenAppWorkflowStepOnError` | Error handling configuration |

#### API Interfaces

| Interface | Description |
|-----------|-------------|
| `ILyshraOpenAppApis` | API definitions container |
| `ILyshraOpenAppApiService` | Service configuration (base URL, auth) |
| `ILyshraOpenAppApiEndpoint` | Endpoint definition |
| `ILyshraOpenAppApiAuthProfile` | Authentication profile |

### 3. lyshra-open-app-plugin-contract-model

Provides builder-pattern model implementations of all contract interfaces:

- `LyshraOpenAppPlugin` - Plugin model with fluent builder
- `LyshraOpenAppProcessorDefinition` - Processor definition model
- `LyshraOpenAppWorkflowDefinition` - Workflow definition model
- `LyshraOpenAppApiDefinitions` - API definitions model
- Authentication config models (Basic, Bearer, OAuth1, OAuth2, AWS Signature, etc.)

### 4. lyshra-open-app-plugins

#### Core Processors Plugin (`lyshra-open-app-core-processors-plugin`)

Built-in processors provided by the platform:

| Processor | Description |
|-----------|-------------|
| `IF_PROCESSOR` | Conditional branching based on expression evaluation |
| `SWITCH_PROCESSOR` | Multi-branch switching |
| `JS_PROCESSOR` | JavaScript code execution via GraalVM |
| `API_PROCESSOR` | HTTP API calls with auth support |

**List Operations:**
| Processor | Description |
|-----------|-------------|
| `ListFilterProcessor` | Filter list items based on criteria |
| `ListSortProcessor` | Sort list items |
| `ListCustomComparatorSortProcessor` | Sort with custom comparator |
| `ListRemoveDuplicatesProcessor` | Remove duplicate items |
| `ListSummarizeProcessor` | Aggregate/summarize list data |

**Date Operations:**
| Processor | Description |
|-----------|-------------|
| `DateAddProcessor` | Add/subtract from dates |
| `DateCompareProcessor` | Compare dates |

**Supported Authentication Types:**
- No Auth
- API Key
- Basic Auth
- Bearer Token
- Digest Auth
- OAuth 1.0
- OAuth 2.0
- Hawk Authentication
- AWS Signature
- NTLM
- Akamai EdgeGrid

#### Sample Workflow Plugin (`lyshra-open-app-sample-workflow-plugin`)

A reference implementation demonstrating how to create plugins with:
- Custom workflows with multiple steps
- Conditional branching (IF processor)
- JavaScript transformation
- API definitions with auth profiles
- i18n support

## Key Concepts

### Plugins

Plugins are the extension mechanism. Each plugin can provide:
- **Processors** - Reusable logic units
- **Workflows** - Pre-defined workflow templates
- **APIs** - HTTP endpoint definitions
- **i18n** - Internationalization resources

Plugins are loaded via Java SPI by implementing `ILyshraOpenAppPluginProvider`:

```java
public class MyPlugin implements ILyshraOpenAppPluginProvider {
    @Override
    public ILyshraOpenAppPlugin create(ILyshraOpenAppPluginFacade facade) {
        return LyshraOpenAppPlugin.builder()
            .identifier(new LyshraOpenAppPluginIdentifier("org", "module", "v1"))
            .processors(builder -> builder
                .processor(MyProcessor::build)
            )
            .workflows(builder -> builder
                .workflow(MyWorkflow::build)
            )
            .build();
    }
}
```

### Workflows

Workflows are composed of steps, where each step:
- References a processor
- Has input configuration
- Defines next step(s) based on result
- Has error handling configuration

```java
.workflow(workflowBuilder -> workflowBuilder
    .name("myWorkflow")
    .startStep("step1")
    .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
    .steps(stepsBuilder -> stepsBuilder
        .step(stepBuilder -> stepBuilder
            .name("step1")
            .processor(new LyshraOpenAppProcessorIdentifier(pluginId, "IF_PROCESSOR"))
            .inputConfig(Map.of("expression", "$data.value > 10"))
            .next(nextBuilder -> nextBuilder.booleanBranch("trueStep", "falseStep"))
            .onError(errorBuilder -> errorBuilder
                .defaultErrorConfig(cfg -> cfg.fallbackOnError("errorStep"))
            )
        )
    )
)
```

### Expression Evaluation

Two expression engines are supported:

| Type | Use Case |
|------|----------|
| **GraalVM JavaScript** | Complex transformations, data manipulation |
| **Spring SpEL** | Simple expressions, response validation |

### Multi-Tenancy

The platform is designed for multi-tenancy where:
- Each application configuration is independent
- Connection details are mutually exclusive
- A single instance can support millions of backend applications
- Datasources don't collide between applications

## Plugin Development Guidelines

1. **Context-Heavy Design** - Plugins should be context-heavy and not rely on Spring components
2. **Raw Java** - Use plain Java without framework dependencies
3. **Independent Connections** - Ensure connection details are independent for each application
4. **Implement Required Interfaces** - All plugins must implement `ILyshraOpenAppPluginProvider`
5. **SPI Registration** - Register in `META-INF/services/com.lyshra.open.app.integration.ILyshraOpenAppPluginProvider`

## Building

```bash
# Build the entire project
./mvnw clean install

# Build a specific module
./mvnw clean install -pl lyshra-open-app-core-engine
```

## Running Tests

```bash
# Run all tests
./mvnw test

# Run tests for a specific module
./mvnw test -pl lyshra-open-app-plugins/lyshra-open-app-core-processors-plugin
```

## Future Roadmap

- Additional date processors (is between, is today, is weekend, format date, extract date parts)
- HTML template processing
- File read/write operations
- Stop, error, sleep, delay, and noop processors
- More database integrations
- Additional authentication methods

## License
Licensed by Lyshra Open App

