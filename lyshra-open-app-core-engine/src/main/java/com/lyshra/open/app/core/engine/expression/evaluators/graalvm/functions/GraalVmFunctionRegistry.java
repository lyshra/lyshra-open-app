package com.lyshra.open.app.core.engine.expression.evaluators.graalvm.functions;

import com.lyshra.open.app.core.engine.expression.evaluators.graalvm.IGraalvmFunction;

import java.util.HashMap;
import java.util.Map;

public class GraalVmFunctionRegistry {
    private final Map<String, IGraalvmFunction> functionRegistry;

    private GraalVmFunctionRegistry() {
        functionRegistry = new HashMap<>();
        this.register(new LogFunction());
        this.register(new NowMinusXMinFunction());
        this.register(new SetContextVariableFunction());
    }

    private static class SingletonHolder {
        private static final GraalVmFunctionRegistry INSTANCE = new GraalVmFunctionRegistry();
    }

    public static GraalVmFunctionRegistry getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private void register(IGraalvmFunction function) {
        this.functionRegistry.put(function.getScriptMemberName(), function);
    }

    public IGraalvmFunction getFunction(String functionName) {
        return this.functionRegistry.get(functionName);
    }

    public Map<String, IGraalvmFunction> getAllFunctions() {
        return functionRegistry;
    }

}
