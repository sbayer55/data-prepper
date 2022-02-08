/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression;

import com.amazon.dataprepper.model.event.Event;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Public class that {@link com.amazon.dataprepper.model.processor.Processor},
 * {@link com.amazon.dataprepper.model.sink.Sink} and data-prepper-core objects can use to evaluate statements.
 */
public class ConditionalStatementEvaluator implements StatementEvaluator<Boolean> {
    private final Parser<ParseTree> parser;
    private final Evaluator<ParseTree, Event> evaluator;

    public ConditionalStatementEvaluator(final Parser<ParseTree> parser, final Evaluator<ParseTree, Event> evaluator) {
        this.parser = parser;
        this.evaluator = evaluator;
    }

    /**
     * {@inheritDoc}
     *
     * <b>Method not implemented</b>
     */
    @Override
    public Boolean evaluate(final String statement, final Event context) throws ClassCastException {
        throw new RuntimeException("Method not implemented");
    }
}
