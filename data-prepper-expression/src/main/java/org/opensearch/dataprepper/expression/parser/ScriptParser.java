/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.opensearch.dataprepper.expression.antlr.DataPrepperStatementLexer;
import org.opensearch.dataprepper.expression.antlr.DataPrepperStatementParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @since 1.3
 * ScriptParser is an abstraction layer to interface with Antlr generated classes from DataPrepperScript.g4 grammar
 * file for parsing statements
 */
public class ScriptParser {
    private static final Logger LOG = LoggerFactory.getLogger(ScriptParser.class);
    private static final CharStream EMPTY_STREAM = CharStreams.fromString("");

    private final DataPrepperStatementLexer lexer;
    private final DataPrepperStatementParser parser;

    public ScriptParser() {
        lexer = new DataPrepperStatementLexer(EMPTY_STREAM);

        final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        parser = new DataPrepperStatementParser(tokenStream);
    }

    /**
     * @since 1.3
     * Parse a statement String to Antlr ParseTree format. Uses DataPrepperStatementLexer used to generate a token stream.
     * Then DataPrepperStatementParser generates a ParseTree by applying grammar rules to the token stream.
     * @param statement String to be parsed
     * @return ParseTree representing hierarchy of the parsed statement by operation precedence
     */
    public ParseTree parse(final String statement) {
        LOG.debug("Parsing statement: {}", statement);

        final IntStream input = CharStreams.fromString(statement);
        lexer.setInputStream(input);

        final TokenStream tokenStream = new CommonTokenStream(lexer);
        parser.setTokenStream(tokenStream);

        return parser.statement();
    }
}
