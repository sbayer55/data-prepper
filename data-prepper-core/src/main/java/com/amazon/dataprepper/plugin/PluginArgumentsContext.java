/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.amazon.dataprepper.plugin;

import com.amazon.dataprepper.metrics.PluginMetrics;
import com.amazon.dataprepper.model.configuration.PipelineDescription;
import com.amazon.dataprepper.model.configuration.PluginSetting;
import com.amazon.dataprepper.model.plugin.PluginFactory;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An internal class which represents all the data which can be provided
 * when constructing a new plugin.
 */
class PluginArgumentsContext {
    private final Map<Class<?>, Supplier<Object>> typedArgumentsSuppliers;
    private final ApplicationContext applicationContext;

    private PluginArgumentsContext(final Builder builder) {
        Objects.requireNonNull(builder.pluginSetting,
                "PluginArgumentsContext received a null Builder object. This is likely an error in the plugin framework.");
        this.applicationContext = Objects.requireNonNull(builder.applicationContext);

        typedArgumentsSuppliers = new HashMap<>();

        typedArgumentsSuppliers.put(builder.pluginSetting.getClass(), () -> builder.pluginSetting);

        if(builder.pluginConfiguration != null) {
            typedArgumentsSuppliers.put(builder.pluginConfiguration.getClass(), () -> builder.pluginConfiguration);
        }

        typedArgumentsSuppliers.put(PluginMetrics.class, () -> PluginMetrics.fromPluginSetting(builder.pluginSetting));

        if (builder.pipelineDescription != null) {
            typedArgumentsSuppliers.put(PipelineDescription.class, () -> builder.pipelineDescription);
        }

        if(builder.pluginFactory != null)
            typedArgumentsSuppliers.put(PluginFactory.class, () -> builder.pluginFactory);
    }

    Object[] createArguments(final Class<?>[] parameterTypes) {
        return Arrays.stream(parameterTypes)
                .map(this::getRequiredArgumentSupplier)
                .map(Supplier::get)
                .toArray();
    }

    private Supplier<Object> getRequiredArgumentSupplier(final Class<?> parameterType) {
        if(typedArgumentsSuppliers.containsKey(parameterType)) {
            return typedArgumentsSuppliers.get(parameterType);
        }
        else {
            return () -> applicationContext.getBean(parameterType);
        }
    }

    static class Builder {
        private Object pluginConfiguration;
        private PluginSetting pluginSetting;
        private PluginFactory pluginFactory;
        private PipelineDescription pipelineDescription;
        private ApplicationContext applicationContext;

        Builder withPluginConfiguration(final Object pluginConfiguration) {
            this.pluginConfiguration = pluginConfiguration;
            return this;
        }

        Builder withPluginSetting(final PluginSetting pluginSetting) {
            this.pluginSetting = pluginSetting;
            return this;
        }

        Builder withPluginFactory(final PluginFactory pluginFactory) {
            this.pluginFactory = pluginFactory;
            return this;
        }

        Builder withPipelineDescription(final PipelineDescription pipelineDescription) {
            this.pipelineDescription = pipelineDescription;
            return this;
        }

        Builder withApplicationContext(final ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
            return this;
        }

        PluginArgumentsContext build() {
            return new PluginArgumentsContext(this);
        }
    }
}
