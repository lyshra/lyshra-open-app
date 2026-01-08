package com.lyshra.open.app.integration.models.processors;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessor;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessors;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Collection of processor definitions with a guided builder, mirroring LyshraOpenAppApiDefinitions style.
 */
@Data
public class LyshraOpenAppProcessorDefinitions implements ILyshraOpenAppProcessors, Serializable {
    private final Map<String, ILyshraOpenAppProcessor> processors;

    // Entry
    public static InitialStepBuilder builder() { return new Builder(); }

    // Steps
    public interface InitialStepBuilder extends BuildStep {
        InitialStepBuilder processor(Function<LyshraOpenAppProcessorDefinition.InitialStepBuilder, LyshraOpenAppProcessorDefinition.BuildStep> fn);
        InitialStepBuilder processor(ILyshraOpenAppProcessor def);
    }
    public interface BuildStep { LyshraOpenAppProcessorDefinitions build(); }

    // Main builder (also acts as collection builder)
    private static class Builder implements InitialStepBuilder, BuildStep {
        private final Map<String, ILyshraOpenAppProcessor> map = new LinkedHashMap<>();

        @Override
        public InitialStepBuilder processor(Function<LyshraOpenAppProcessorDefinition.InitialStepBuilder, LyshraOpenAppProcessorDefinition.BuildStep> fn) {
            LyshraOpenAppProcessorDefinition def = fn.apply(LyshraOpenAppProcessorDefinition.builder()).build();
            map.put(def.getName(), def);
            return this;
        }

        @Override
        public InitialStepBuilder processor(ILyshraOpenAppProcessor def) {
            map.put(def.getName(), def);
            return this;
        }

        @Override
        public LyshraOpenAppProcessorDefinitions build() {
            return new LyshraOpenAppProcessorDefinitions(Collections.unmodifiableMap(map));
        }
    }
}
