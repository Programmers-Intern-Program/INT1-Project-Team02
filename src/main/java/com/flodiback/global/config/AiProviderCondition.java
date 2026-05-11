package com.flodiback.global.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public final class AiProviderCondition {

    private static final String PROVIDER_PROPERTY = "ai.provider";
    private static final String GLM_ENABLED_PROPERTY = "glm.enabled";
    private static final String OPENAI_PROVIDER = "openai";
    private static final String GLM_PROVIDER = "glm";
    private static final String DISABLED_PROVIDER = "disabled";

    private AiProviderCondition() {}

    public static final class OpenAiProviderCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return OPENAI_PROVIDER.equalsIgnoreCase(provider(context));
        }
    }

    public static final class GlmProviderCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String provider = provider(context);
            if (StringUtils.hasText(provider)) {
                return GLM_PROVIDER.equalsIgnoreCase(provider);
            }
            return Boolean.parseBoolean(context.getEnvironment().getProperty(GLM_ENABLED_PROPERTY, "false"));
        }
    }

    public static final class DisabledProviderCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String provider = provider(context);
            if (StringUtils.hasText(provider)) {
                return DISABLED_PROVIDER.equalsIgnoreCase(provider);
            }
            return !Boolean.parseBoolean(context.getEnvironment().getProperty(GLM_ENABLED_PROPERTY, "false"));
        }
    }

    private static String provider(ConditionContext context) {
        return context.getEnvironment().getProperty(PROVIDER_PROPERTY, "");
    }
}
