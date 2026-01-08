package com.lyshra.open.app.core.engine;

import com.lyshra.open.app.core.engine.config.LyshraOpenAppSystemConfigEngine;
import com.lyshra.open.app.core.engine.documentation.ILyshraOpenAppPluginDocumentationService;
import com.lyshra.open.app.core.engine.documentation.impl.LyshraOpenAppPluginDocumentationService;
import com.lyshra.open.app.core.engine.expression.LyshraOpenAppExpressionEvaluator;
import com.lyshra.open.app.core.engine.message.ILyshraOpenAppPluginMessageSource;
import com.lyshra.open.app.core.engine.message.LyshraOpenAppPluginMessageSource;
import com.lyshra.open.app.core.engine.misc.LyshraOpenAppObjectMapper;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppProcessorExecutor;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowExecutor;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowStepExecutor;
import com.lyshra.open.app.core.engine.node.impl.LyshraOpenAppProcessorExecutor;
import com.lyshra.open.app.core.engine.node.impl.LyshraOpenAppWorkflowExecutor;
import com.lyshra.open.app.core.engine.node.impl.LyshraOpenAppWorkflowStepExecutor;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginFactory;
import com.lyshra.open.app.core.engine.plugin.impl.LyshraOpenAppPluginFactory;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpressionEvaluator;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppObjectMapper;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppSystemConfigEngine;

public class LyshraOpenAppFacade implements ILyshraOpenAppFacade {

    public static final String I18N_MESSAGES_BASE_PATH = "i18n/lyshra_open_app_core_messages";
    private final ILyshraOpenAppPluginMessageSource coreEngineMessageSource;

    private LyshraOpenAppFacade() {
        coreEngineMessageSource = new LyshraOpenAppPluginMessageSource(() -> I18N_MESSAGES_BASE_PATH, this.getClass().getClassLoader());
    }

    private static final class SingletonHelper {
        private static final ILyshraOpenAppFacade INSTANCE = new LyshraOpenAppFacade();
    }

    public static ILyshraOpenAppFacade getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public ILyshraOpenAppPluginMessageSource getCoreEngineMessageSource() {
        return coreEngineMessageSource;
    }

    @Override
    public ILyshraOpenAppPluginFactory getPluginFactory() {
        return LyshraOpenAppPluginFactory.getInstance();
    }

    @Override
    public ILyshraOpenAppWorkflowExecutor getWorkflowExecutor() {
        return LyshraOpenAppWorkflowExecutor.getInstance();
    }

    @Override
    public ILyshraOpenAppWorkflowStepExecutor getWorkflowStepExecutor() {
        return LyshraOpenAppWorkflowStepExecutor.getInstance();
    }

    @Override
    public ILyshraOpenAppProcessorExecutor getProcessorExecutor() {
        return LyshraOpenAppProcessorExecutor.getInstance();
    }

    @Override
    public ILyshraOpenAppExpressionEvaluator getExpressionExecutor() {
        return LyshraOpenAppExpressionEvaluator.getInstance();
    }

    @Override
    public ILyshraOpenAppObjectMapper getObjectMapper() {
        return LyshraOpenAppObjectMapper.getInstance();
    }

    @Override
    public ILyshraOpenAppSystemConfigEngine getConfigEngine() {
        return LyshraOpenAppSystemConfigEngine.getInstance();
    }

    public ILyshraOpenAppPluginDocumentationService getPluginDocumentationService() {
        return LyshraOpenAppPluginDocumentationService.getInstance();
    }
}
