package com.lyshra.open.app.core.exception.node;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppProcessorNotFound extends RuntimeException {
    private final ILyshraOpenAppProcessorIdentifier identifier;

    public LyshraOpenAppProcessorNotFound(ILyshraOpenAppProcessorIdentifier identifier) {
        super("Processor Not Found. Identifier: ["+ identifier +"]");
        this.identifier = identifier;
    }
}
