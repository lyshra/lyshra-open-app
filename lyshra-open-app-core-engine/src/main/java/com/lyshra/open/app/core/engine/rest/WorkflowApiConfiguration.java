package com.lyshra.open.app.core.engine.rest;

import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.humantask.ILyshraOpenAppHumanTaskService;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowResumptionService;
import com.lyshra.open.app.core.engine.node.impl.LyshraOpenAppWorkflowResumptionServiceImpl;
import com.lyshra.open.app.core.engine.state.ILyshraOpenAppWorkflowStateStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Workflow REST API controllers.
 *
 * <p>This configuration provides the necessary beans for the REST controllers
 * to interact with the workflow engine services.</p>
 *
 * <h2>Usage</h2>
 * <p>Import this configuration in your Spring Boot application:</p>
 * <pre>{@code
 * @SpringBootApplication
 * @Import(WorkflowApiConfiguration.class)
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * <h2>Endpoints Enabled</h2>
 * <ul>
 *   <li>/api/v1/tasks/* - Human task management endpoints</li>
 *   <li>/api/v1/workflows/* - Workflow state management endpoints</li>
 * </ul>
 */
@Configuration
public class WorkflowApiConfiguration {

    /**
     * Provides the human task service bean.
     */
    @Bean
    public ILyshraOpenAppHumanTaskService humanTaskService() {
        return LyshraOpenAppFacade.getInstance().getHumanTaskService();
    }

    /**
     * Provides the workflow state store bean.
     */
    @Bean
    public ILyshraOpenAppWorkflowStateStore workflowStateStore() {
        return LyshraOpenAppFacade.getInstance().getWorkflowStateStore();
    }

    /**
     * Provides the workflow resumption service bean.
     */
    @Bean
    public ILyshraOpenAppWorkflowResumptionService workflowResumptionService() {
        return LyshraOpenAppWorkflowResumptionServiceImpl.getInstance();
    }

    /**
     * Provides the human task controller bean.
     */
    @Bean
    public HumanTaskController humanTaskController(ILyshraOpenAppHumanTaskService humanTaskService) {
        return new HumanTaskController(humanTaskService);
    }

    /**
     * Provides the workflow controller bean.
     */
    @Bean
    public WorkflowController workflowController(
            ILyshraOpenAppWorkflowStateStore workflowStateStore,
            ILyshraOpenAppWorkflowResumptionService resumptionService) {
        return new WorkflowController(workflowStateStore, resumptionService);
    }
}
