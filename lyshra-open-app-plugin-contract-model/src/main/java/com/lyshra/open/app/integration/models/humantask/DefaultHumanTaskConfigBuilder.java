package com.lyshra.open.app.integration.models.humantask;

import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskEscalation;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskFormSchema;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor.ILyshraOpenAppHumanTaskConfig;
import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskProcessor.ILyshraOpenAppHumanTaskConfigBuilder;
import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of the human task configuration builder.
 */
public class DefaultHumanTaskConfigBuilder implements ILyshraOpenAppHumanTaskConfigBuilder {

    private String title = "Human Task";
    private String description = "";
    private int priority = 5;
    private List<String> assignees = new ArrayList<>();
    private List<String> candidateGroups = new ArrayList<>();
    private Duration timeout;
    private ILyshraOpenAppHumanTaskEscalation escalation;
    private ILyshraOpenAppHumanTaskFormSchema formSchema;

    @Override
    public ILyshraOpenAppHumanTaskConfigBuilder title(String title) {
        this.title = title;
        return this;
    }

    @Override
    public ILyshraOpenAppHumanTaskConfigBuilder description(String description) {
        this.description = description;
        return this;
    }

    @Override
    public ILyshraOpenAppHumanTaskConfigBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public ILyshraOpenAppHumanTaskConfigBuilder assignees(List<String> assignees) {
        this.assignees = new ArrayList<>(assignees);
        return this;
    }

    @Override
    public ILyshraOpenAppHumanTaskConfigBuilder candidateGroups(List<String> groups) {
        this.candidateGroups = new ArrayList<>(groups);
        return this;
    }

    @Override
    public ILyshraOpenAppHumanTaskConfigBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public ILyshraOpenAppHumanTaskConfigBuilder escalation(ILyshraOpenAppHumanTaskEscalation escalation) {
        this.escalation = escalation;
        return this;
    }

    @Override
    public ILyshraOpenAppHumanTaskConfigBuilder formSchema(ILyshraOpenAppHumanTaskFormSchema formSchema) {
        this.formSchema = formSchema;
        return this;
    }

    @Override
    public ILyshraOpenAppHumanTaskConfig build() {
        return new DefaultHumanTaskConfig(
                title,
                description,
                priority,
                Collections.unmodifiableList(assignees),
                Collections.unmodifiableList(candidateGroups),
                timeout,
                escalation,
                formSchema
        );
    }

    @Data
    private static class DefaultHumanTaskConfig implements ILyshraOpenAppHumanTaskConfig {
        private final String title;
        private final String description;
        private final int priority;
        private final List<String> assignees;
        private final List<String> candidateGroups;
        private final Duration timeout;
        private final ILyshraOpenAppHumanTaskEscalation escalation;
        private final ILyshraOpenAppHumanTaskFormSchema formSchema;

        @Override
        public Optional<Duration> getTimeout() {
            return Optional.ofNullable(timeout);
        }

        @Override
        public Optional<ILyshraOpenAppHumanTaskEscalation> getEscalation() {
            return Optional.ofNullable(escalation);
        }

        @Override
        public Optional<ILyshraOpenAppHumanTaskFormSchema> getFormSchema() {
            return Optional.ofNullable(formSchema);
        }
    }
}
