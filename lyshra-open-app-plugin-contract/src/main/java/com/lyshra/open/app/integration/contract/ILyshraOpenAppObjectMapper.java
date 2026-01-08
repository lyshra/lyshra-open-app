package com.lyshra.open.app.integration.contract;

public interface ILyshraOpenAppObjectMapper {
    <T> T convertValue(Object fromValue, Class<T> toValueType) throws IllegalArgumentException;
}
