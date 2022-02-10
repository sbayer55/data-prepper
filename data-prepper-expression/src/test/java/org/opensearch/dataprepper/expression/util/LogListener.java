/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression.util;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.opensearch.dataprepper.expression.antlr.DataPrepperStatementListener;
import org.opensearch.dataprepper.expression.antlr.DataPrepperStatementParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @since 1.3
 * LogListener is a utility listener that logs every event enter and exit. Useful for debugging and developing new
 * listeners.
 */
public class LogListener implements DataPrepperStatementListener {
    private static final Logger LOG = LoggerFactory.getLogger(LogListener.class);

    private int level = 0;

    /**
     * @since 1.3
     * Utility function to repeat String str, n number of times.
     * @param n is the number of times str will be printed
     * @param str the string to be repeated.
     * @return String containing n instances of str
     */
    private String nCopiesOf(final int n, final String str) {
        return String.join("", Collections.nCopies(Math.max(n, 1), str));
    }

    /**
     * @since 1.3
     * Get self or child nodes of type Terminal Node (Indivisible Rule/Token)
     * @param ctx Current parsing context
     * @return List of self or all child nodes that are Terminal Nodes. List may be empty if no terminal nodes found.
     */
    private List<TerminalNode> getTerminalNodes(final ParserRuleContext ctx) {
        if (ctx instanceof TerminalNode) {
            return Collections.singletonList((TerminalNode) ctx);
        }
        else if (ctx.children == null) {
            return Collections.emptyList();
        }
        else {
            return ctx.children.stream()
                    .flatMap(tree -> {
                        if (tree instanceof TerminalNode) {
                            return Stream.of((TerminalNode) tree);
                        }
                        else {
                            return Stream.empty();
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * @since 1.3
     * Get terminal nodes a convert to a string of comma separated values
     * @param ctx Current parsing context
     * @return string of comma separated values
     */
    private String getTerminalString(final ParserRuleContext ctx) {
        final List<TerminalNode> terminalNodes = getTerminalNodes(ctx);
        return terminalNodes.stream()
                .map(TerminalNode::getText)
                .map(text -> "'" + text + "'")
                .collect(Collectors.joining(", "));
    }

    /**
     * @since 1.3
     * Creates an indented prefix for pretty format printed hierarchical structures
     * @return String of tabs based on level in hierarchy
     */
    private String prefix() {
        return String.join("|", Collections.nCopies(level, "\t"));
    }

    @Override
    public void enterStatement(final DataPrepperStatementParser.StatementContext ctx) {
        LOG.info("{}enterStatement: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitStatement(final DataPrepperStatementParser.StatementContext ctx) {
        level--;
        LOG.info("{}exitStatement: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterExpression(final DataPrepperStatementParser.ExpressionContext ctx) {
        final String terminals = getTerminalString(ctx);
        LOG.info("{}enterExpression: {} -> Terminal: {}", prefix(), ctx.getText(), terminals);
        level++;
    }

    @Override
    public void exitExpression(final DataPrepperStatementParser.ExpressionContext ctx) {
        final String terminals = getTerminalString(ctx);
        level--;
        LOG.info("{}exitExpression: {} -> Terminal: {}", prefix(), ctx.getText(), terminals);
    }

    @Override
    public void enterListOperatorExpression(final DataPrepperStatementParser.ListOperatorExpressionContext ctx) {
        LOG.info("{}enterListOperatorExpression: {}", prefix(), ctx.getText());
        level++;
    }


    @Override
    public void exitListOperatorExpression(final DataPrepperStatementParser.ListOperatorExpressionContext ctx) {
        level--;
        LOG.info("{}exitListOperatorExpression: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterBinaryOperatorExpression(final DataPrepperStatementParser.BinaryOperatorExpressionContext ctx) {
        LOG.info("{}enterBinaryOperatorExpression: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitBinaryOperatorExpression(final DataPrepperStatementParser.BinaryOperatorExpressionContext ctx) {
        level--;
        LOG.info("{}exitBinaryOperatorExpression: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterConditionalExpression(final DataPrepperStatementParser.ConditionalExpressionContext ctx) {
        LOG.info("{}enterConditionalExpression: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitConditionalExpression(final DataPrepperStatementParser.ConditionalExpressionContext ctx) {
        level--;
        LOG.info("{}exitConditionalExpression: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterUnaryOperatorExpression(final DataPrepperStatementParser.UnaryOperatorExpressionContext ctx) {
        LOG.info("{}enterUnaryOperatorExpression: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitUnaryOperatorExpression(final DataPrepperStatementParser.UnaryOperatorExpressionContext ctx) {
        level--;
        LOG.info("{}exitUnaryOperatorExpression: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterUnaryNotOperatorExpression(final DataPrepperStatementParser.UnaryNotOperatorExpressionContext ctx) {
        LOG.info("{}enterUnaryNotOperatorExpression: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitUnaryNotOperatorExpression(final DataPrepperStatementParser.UnaryNotOperatorExpressionContext ctx) {
        level--;
        LOG.info("{}exitUnaryNotOperatorExpression: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterRegexOperatorExpression(final DataPrepperStatementParser.RegexOperatorExpressionContext ctx) {
        LOG.info("{}enterRegexOperatorExpression: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitRegexOperatorExpression(final DataPrepperStatementParser.RegexOperatorExpressionContext ctx) {
        level--;
        LOG.info("{}exitRegexOperatorExpression: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterRelationalOperatorExpression(final DataPrepperStatementParser.RelationalOperatorExpressionContext ctx) {
        LOG.info("{}enterRegexOperatorExpression: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitRelationalOperatorExpression(final DataPrepperStatementParser.RelationalOperatorExpressionContext ctx) {
        level--;
        LOG.info("{}exitRegexOperatorExpression: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterBinaryOperator(final DataPrepperStatementParser.BinaryOperatorContext ctx) {
        LOG.info("{}enterBinaryOperator: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitBinaryOperator(final DataPrepperStatementParser.BinaryOperatorContext ctx) {
        level--;
        LOG.info("{}exitBinaryOperator: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterEqualityOperatorExpression(final DataPrepperStatementParser.EqualityOperatorExpressionContext ctx) {
        LOG.info("{}enterEqualityOperatorExpression: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitEqualityOperatorExpression(final DataPrepperStatementParser.EqualityOperatorExpressionContext ctx) {
        level--;
        LOG.info("{}exitEqualityOperatorExpression: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterRegexEqualityOperator(final DataPrepperStatementParser.RegexEqualityOperatorContext ctx) {
        LOG.info("{}enterRegexEqualityOperator: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitRegexEqualityOperator(final DataPrepperStatementParser.RegexEqualityOperatorContext ctx) {
        level--;
        LOG.info("{}exitRegexEqualityOperator: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterListOperator(final DataPrepperStatementParser.ListOperatorContext ctx) {
        LOG.info("{}enterListOperator: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitListOperator(final DataPrepperStatementParser.ListOperatorContext ctx) {
        level--;
        LOG.info("{}exitListOperator: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterConditionalOperator(final DataPrepperStatementParser.ConditionalOperatorContext ctx) {
        LOG.info("{}enterConditionalOperator: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitConditionalOperator(final DataPrepperStatementParser.ConditionalOperatorContext ctx) {
        level--;
        LOG.info("{}exitConditionalOperator: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterUnaryOperator(final DataPrepperStatementParser.UnaryOperatorContext ctx) {
        LOG.info("{}enterUnaryOperator: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitUnaryOperator(final DataPrepperStatementParser.UnaryOperatorContext ctx) {
        level--;
        LOG.info("{}exitUnaryOperator: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterEqualityOperator(final DataPrepperStatementParser.EqualityOperatorContext ctx) {
        LOG.info("{}enterEqualityOperator: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitEqualityOperator(final DataPrepperStatementParser.EqualityOperatorContext ctx) {
        level--;
        LOG.info("{}exitEqualityOperator: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterRelationalOperator(final DataPrepperStatementParser.RelationalOperatorContext ctx) {
        LOG.info("{}enterRelationalOperator: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitRelationalOperator(final DataPrepperStatementParser.RelationalOperatorContext ctx) {
        level--;
        LOG.info("{}exitRelationalOperator: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterPrimary(final DataPrepperStatementParser.PrimaryContext ctx) {
        LOG.info("{}enterPrimary: {}", prefix(), ctx.getText());
        level++;

    }

    @Override
    public void exitPrimary(final DataPrepperStatementParser.PrimaryContext ctx) {
        level--;
        LOG.info("{}exitPrimary: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterRegexPattern(final DataPrepperStatementParser.RegexPatternContext ctx) {
        LOG.info("{}enterRegexPattern: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitRegexPattern(final DataPrepperStatementParser.RegexPatternContext ctx) {
        level--;
        LOG.info("{}exitRegexPattern: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterExpressionInitializer(final DataPrepperStatementParser.ExpressionInitializerContext ctx) {
        LOG.info("{}enterExpressionInitializer: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitExpressionInitializer(final DataPrepperStatementParser.ExpressionInitializerContext ctx) {
        level--;
        LOG.info("{}exitExpressionInitializer: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterListInitializer(final DataPrepperStatementParser.ListInitializerContext ctx) {
        LOG.info("{}enterListInitializer: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitListInitializer(final DataPrepperStatementParser.ListInitializerContext ctx) {
        level--;
        LOG.info("{}exitListInitializer: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterVariableIdentifier(final DataPrepperStatementParser.VariableIdentifierContext ctx) {
        LOG.info("{}enterVariableIdentifier: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitVariableIdentifier(final DataPrepperStatementParser.VariableIdentifierContext ctx) {
        level--;
        LOG.info("{}exitVariableIdentifier: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterVariableName(final DataPrepperStatementParser.VariableNameContext ctx) {
        LOG.info("{}enterVariableName: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitVariableName(final DataPrepperStatementParser.VariableNameContext ctx) {
        level--;
        LOG.info("{}exitVariableName: {}", prefix(), ctx.getText());
    }

    @Override
    public void enterLiteral(final DataPrepperStatementParser.LiteralContext ctx) {
        LOG.info("{}enterLiteral: {}", prefix(), ctx.getText());
        level++;
    }

    @Override
    public void exitLiteral(final DataPrepperStatementParser.LiteralContext ctx) {
        level--;
        LOG.info("{}exitLiteral: {}", prefix(), ctx.getText());
    }

    @Override
    public void visitTerminal(final TerminalNode node) {
        LOG.info("{}visitTerminal: {}", prefix(), node.getSymbol().getText());
    }

    /**
     * @since 1.3
     * Logs all error nodes visited with indicator where the parsing error occurred in the statement. Trigger on Lexer
     * error only, parser error will be available in context variable on all other listener functions Sample output:
     * <pre>
     * Parsing error expected token '[' at position 5<br>
     * 5 in true<br>
     *      ^
     * </pre>
     * @param node cause of lexer error
     */
    @Override
    public void visitErrorNode(final ErrorNode node) {
        LOG.warn("{}visitErrorNode: {}", prefix(), node.getSymbol().getText());

        final Token symbol = node.getSymbol();
        final String sourceStatement = symbol.getInputStream().toString();
        final String locationIdentifier = nCopiesOf(symbol.getCharPositionInLine(), " ") + '^';
        LOG.error(
                "{}Parsing error {} at position {}\n{}\n{}",
                prefix(),
                node.getSymbol().getText(),
                symbol.getCharPositionInLine(),
                sourceStatement,
                locationIdentifier
        );
    }

    @Override
    public void enterEveryRule(final ParserRuleContext ctx) {
        LOG.trace("{}enterEveryRule: {}", prefix(), ctx.getText());
        if (ctx.exception != null) {
            LOG.error("{}Parser exception {} thrown parsing {} on enter rule", prefix(), ctx.exception, ctx.getText());
        }
    }

    @Override
    public void exitEveryRule(final ParserRuleContext ctx) {
        LOG.trace("{}exitEveryRule: {}", prefix(), ctx.getText());
        if (ctx.exception != null) {
            // Log errors will be printed by enterEveryRule
            LOG.trace("{}Parser exception {} thrown parsing {} on exit rule", prefix(), ctx.exception, ctx.getText());
        }
    }
}
