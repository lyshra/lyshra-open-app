package com.lyshra.open.app.core.engine.misc;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppObjectMapper;
import tools.jackson.databind.ObjectMapper;

public class LyshraOpenAppObjectMapper extends ObjectMapper implements ILyshraOpenAppObjectMapper {

    private LyshraOpenAppObjectMapper() {}

    private static final class SingletonHolder {
        private static final LyshraOpenAppObjectMapper INSTANCE = new LyshraOpenAppObjectMapper();
    }

    public static ILyshraOpenAppObjectMapper getInstance() {
        return SingletonHolder.INSTANCE;
    }
}
