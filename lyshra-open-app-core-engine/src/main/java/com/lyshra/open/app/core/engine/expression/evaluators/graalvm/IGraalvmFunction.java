package com.lyshra.open.app.core.engine.expression.evaluators.graalvm;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;

public interface IGraalvmFunction {
    String getScriptMemberName();
    List<String> getSampleUsage();

    default List<Object> parseInput(Value[] args, ILyshraOpenAppPluginFacade facade) {
        List<Object> argsList = new ArrayList<>();
        for (Value arg : args) {
            argsList.add(GraalVmValueParser.parseResult(arg, facade));
        }
        return argsList;
    }

    void validate(List<Object> arguments, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) throws InvalidGraalVmFunctionInputException;
    Object execute(List<Object> arguments, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
}
