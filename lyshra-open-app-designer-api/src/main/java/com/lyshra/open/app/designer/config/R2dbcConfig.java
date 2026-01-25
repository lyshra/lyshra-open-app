package com.lyshra.open.app.designer.config;

import com.lyshra.open.app.designer.domain.ExecutionStatus;
import com.lyshra.open.app.designer.domain.WorkflowLifecycleState;
import com.lyshra.open.app.designer.domain.WorkflowVersionState;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.ArrayList;
import java.util.List;

/**
 * R2DBC configuration for reactive database access.
 * Configures custom converters for enum types and enables auditing.
 */
@Configuration
@EnableR2dbcAuditing
@EnableR2dbcRepositories(basePackages = "com.lyshra.open.app.designer.persistence.repository")
public class R2dbcConfig {

    /**
     * Custom conversions for R2DBC to handle enum types.
     */
    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory connectionFactory) {
        var dialect = DialectResolver.getDialect(connectionFactory);
        var converters = new ArrayList<Object>();

        // WorkflowLifecycleState converters
        converters.add(new WorkflowLifecycleStateReadConverter());
        converters.add(new WorkflowLifecycleStateWriteConverter());

        // WorkflowVersionState converters
        converters.add(new WorkflowVersionStateReadConverter());
        converters.add(new WorkflowVersionStateWriteConverter());

        // ExecutionStatus converters
        converters.add(new ExecutionStatusReadConverter());
        converters.add(new ExecutionStatusWriteConverter());

        return R2dbcCustomConversions.of(dialect, converters);
    }

    // ============================================
    // WorkflowLifecycleState Converters
    // ============================================

    static class WorkflowLifecycleStateReadConverter implements Converter<String, WorkflowLifecycleState> {
        @Override
        public WorkflowLifecycleState convert(String source) {
            return source != null ? WorkflowLifecycleState.valueOf(source) : null;
        }
    }

    static class WorkflowLifecycleStateWriteConverter implements Converter<WorkflowLifecycleState, String> {
        @Override
        public String convert(WorkflowLifecycleState source) {
            return source != null ? source.name() : null;
        }
    }

    // ============================================
    // WorkflowVersionState Converters
    // ============================================

    static class WorkflowVersionStateReadConverter implements Converter<String, WorkflowVersionState> {
        @Override
        public WorkflowVersionState convert(String source) {
            return source != null ? WorkflowVersionState.valueOf(source) : null;
        }
    }

    static class WorkflowVersionStateWriteConverter implements Converter<WorkflowVersionState, String> {
        @Override
        public String convert(WorkflowVersionState source) {
            return source != null ? source.name() : null;
        }
    }

    // ============================================
    // ExecutionStatus Converters
    // ============================================

    static class ExecutionStatusReadConverter implements Converter<String, ExecutionStatus> {
        @Override
        public ExecutionStatus convert(String source) {
            return source != null ? ExecutionStatus.valueOf(source) : null;
        }
    }

    static class ExecutionStatusWriteConverter implements Converter<ExecutionStatus, String> {
        @Override
        public String convert(ExecutionStatus source) {
            return source != null ? source.name() : null;
        }
    }
}
