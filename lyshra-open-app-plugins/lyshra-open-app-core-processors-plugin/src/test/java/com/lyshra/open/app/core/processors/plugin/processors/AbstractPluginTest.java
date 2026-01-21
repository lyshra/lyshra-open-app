package com.lyshra.open.app.core.processors.plugin.processors;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.models.LyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;

public abstract class AbstractPluginTest {

    public static final ILyshraOpenAppFacade facade = LyshraOpenAppFacade.getInstance();

    public static ILyshraOpenAppContext createTestContext(String dataResourceFileName, String variableResourcesFileName) throws Exception {
        ILyshraOpenAppContext testContext = new LyshraOpenAppContext();

        ClassLoader classLoader = AbstractPluginTest.class.getClassLoader();
        try(InputStream resourceAsStream = classLoader.getResourceAsStream(dataResourceFileName)) {
            Map map = ((ObjectMapper) facade.getObjectMapper()).readValue(resourceAsStream, Map.class);
            testContext.setData(map);
        }

        try(InputStream resourceAsStream = classLoader.getResourceAsStream(variableResourcesFileName)) {
            Map map = ((ObjectMapper) facade.getObjectMapper()).readValue(resourceAsStream, Map.class);
            testContext.setVariables(map);
        }

        return testContext;
    }
}
