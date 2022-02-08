/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression;

import com.amazon.dataprepper.model.event.Event;
import org.antlr.v4.runtime.tree.ParseTree;
import org.opensearch.dataprepper.expression.evaluate.Evaluator;
import org.opensearch.dataprepper.expression.parser.ScriptParser;

public class ConditionalStatementEvaluator implements StatementEvaluator<Boolean> {
    private final ScriptParser parser;
    private final Evaluator<ParseTree> evaluator;

    public ConditionalStatementEvaluator(final ScriptParser parser, final Evaluator<ParseTree> evaluator) {
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
