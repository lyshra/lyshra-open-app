package com.lyshra.open.app.core.engine.misc;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppObjectMapper;
import tools.jackson.databind.ObjectMapper;

public class LyshraOpenAppObjectMapper implements ILyshraOpenAppObjectMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LyshraOpenAppObjectMapper() {}

    @Override
    public <T> T convertValue(Object fromValue, Class<T> toValueType) throws IllegalArgumentException {
        return objectMapper.convertValue(fromValue, toValueType);
    }

    private static final class SingletonHolder {
        private static final LyshraOpenAppObjectMapper INSTANCE = new LyshraOpenAppObjectMapper();
    }

    public static ILyshraOpenAppObjectMapper getInstance() {
        return SingletonHolder.INSTANCE;
    }
}
