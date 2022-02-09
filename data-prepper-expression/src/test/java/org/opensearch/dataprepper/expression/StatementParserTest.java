/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.dataprepper.expression.antlr.DataPrepperStatementLexer;
import org.opensearch.dataprepper.expression.antlr.DataPrepperStatementParser;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;

class StatementParserTest {
    private StatementParser statementParser;

    @BeforeEach
    void beforeEach() {
        final Lexer lexer = new DataPrepperStatementLexer(CharStreams.fromString(""));
        final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        final DataPrepperStatementParser parser = new DataPrepperStatementParser(tokenStream);
        statementParser = new StatementParser(parser);
    }

    @Test
    void testParse() {
        final String statement = "5==5";

        final ParseTree actual = statementParser.parse(statement);

        assertThat(actual, isA(ParseTree.class));
        assertThat(actual.getText(), is(statement));
    }
}