/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.pipeline.router;

import org.opensearch.dataprepper.expression.ExpressionEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RouterAppConfig {
    @Bean
    public RouterFactory routeEvaluatorFactory(final ExpressionEvaluator<Boolean> expressionEvaluator) {
        return new RouterFactory(expressionEvaluator);
    }
}
