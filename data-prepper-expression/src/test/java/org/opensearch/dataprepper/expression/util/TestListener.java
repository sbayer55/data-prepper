/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.opensearch.dataprepper.expression.antlr.DataPrepperStatementBaseListener;
import org.opensearch.dataprepper.expression.antlr.DataPrepperStatementParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * @since 1.3
 * Listener used for testing to convert {@link org.antlr.v4.runtime.tree.ParseTree} objects to a nested string format
 * for easy assertions. Parsing errors are tracked in {@link TestListener#errorNodeList} and
 * {@link TestListener#exceptionList}. For hamcrest assertions {@link ListenerMatcher}.
 */
public class TestListener extends DataPrepperStatementBaseListener {
    private static final Logger LOG = LoggerFactory.getLogger(TestListener.class);

    private static final String ESCAPED_FORWARD_SLASH = "|escaped-forward-slash|";
    private static final String ESCAPED_DOUBLE_QUOTE = "|escaped-double-quote|";

    private static final List<Integer> PARENTHESIS_SYMBOL_TYPES = Arrays.asList(
            DataPrepperStatementParser.LPAREN,
            DataPrepperStatementParser.RPAREN
    );
    private static final List<Integer> KEY_SYMBOL_TYPES = Arrays.asList(
            DataPrepperStatementParser.EOF,
            DataPrepperStatementParser.EQUAL,
            DataPrepperStatementParser.NOT_EQUAL,
            DataPrepperStatementParser.LT,
            DataPrepperStatementParser.LTE,
            DataPrepperStatementParser.GT,
            DataPrepperStatementParser.GTE,
            DataPrepperStatementParser.MATCH_REGEX_PATTERN,
            DataPrepperStatementParser.NOT_MATCH_REGEX_PATTERN,
            DataPrepperStatementParser.IN_LIST,
            DataPrepperStatementParser.NOT_IN_LIST,
            DataPrepperStatementParser.AND,
            DataPrepperStatementParser.OR,
            DataPrepperStatementParser.NOT,
            DataPrepperStatementParser.LBRACK,
            DataPrepperStatementParser.RBRACK,
            DataPrepperStatementParser.TRUE,
            DataPrepperStatementParser.FALSE,
            DataPrepperStatementParser.FORWARDSLASH,
            DataPrepperStatementParser.DOUBLEQUOTE,
            DataPrepperStatementParser.LISTSEPARATOR,
            DataPrepperStatementParser.SPACE,
            DataPrepperStatementParser.OTHER
    );

    private final ObjectMapper mapper = new ObjectMapper();
    private final ArrayNode statementArray = mapper.createArrayNode();
    private final List<ErrorNode> errorNodeList = new LinkedList<>();
    private final List<Exception> exceptionList = new LinkedList<>();
    private final Stack<ArrayNode> stack = new Stack<>();
    private final List<String> verboseTokenList = new LinkedList<>();
    private String verboseString = "";

    public TestListener() {
    }

    @Override
    public void visitTerminal(final TerminalNode node) {
        super.visitTerminal(node);

        verboseTokenList.add(node.getSymbol().getText());

        if (node.getSymbol().getType() == DataPrepperStatementParser.Integer) {
            final Integer terminal = Integer.parseInt(node.getSymbol().getText());
            stack.peek().add(terminal);
        }
        else if (node.getSymbol().getType() == DataPrepperStatementParser.Float) {
            final Float terminal = Float.parseFloat(node.getSymbol().getText());
            stack.peek().add(terminal);
        }
        else if (node.getSymbol().getType() == DataPrepperStatementParser.Boolean) {
            final Boolean terminal = Boolean.parseBoolean(node.getSymbol().getText());
            stack.peek().add(terminal);
        }
        else if (node.getSymbol().getType() == DataPrepperStatementParser.JsonPointer) {
            String jsonPointer = node.getSymbol().getText();
            if (jsonPointer.startsWith("\"")) {
                // Remove surrounding " on json pointer
                jsonPointer = jsonPointer.substring(1, jsonPointer.length() - 1);
                // Handle escape sequence that jackson will escape incorrectly, triggering a recursive escape sequence
                jsonPointer = jsonPointer.replace("\\/", ESCAPED_FORWARD_SLASH);
                // Remove \ characters, Jackson will duplicate them recursively
                jsonPointer = jsonPointer.replace("\\", "");
            }
            stack.peek().add(jsonPointer);
        }
        else if (node.getSymbol().getType() == DataPrepperStatementParser.String) {
            String stringNode = node.getSymbol().getText();
            // Remove surrounding " on strings
            stringNode = stringNode.substring(1, stringNode.length() - 1);
            // Remove \ characters, Jackson will duplicate them recursively
            stringNode = stringNode.replace("\\", "");
            stack.peek().add(stringNode);
        }
        else if (KEY_SYMBOL_TYPES.contains(node.getSymbol().getType())) {
            stack.peek().add(node.getSymbol().getText());
        }
        else if (PARENTHESIS_SYMBOL_TYPES.contains(node.getSymbol().getType())) {
            LOG.debug("Token {} not added to statement array", node.getSymbol().getText());
        }
        else if (node.getSymbol().getType() == DataPrepperStatementParser.EOF) {
            LOG.debug("End of statement reached");
        }
        else {
            LOG.error("Unknown symbol type {} for node \"{}\"", node.getSymbol().getType(), node.getSymbol().getText());
        }
    }

    private void enterNode(final ParserRuleContext ctx) {
        if (ctx.exception != null) {
            LOG.warn("Parse Exception {} found on \"{}\"", ctx.exception, ctx.getText());
            exceptionList.add(ctx.exception);
        }
        if (stack.empty()) {
            if (!statementArray.isEmpty()) {
                LOG.error("Stack unexpectedly empty, possible reused listener?");
                LOG.warn("Clearing statement array {}", statementArray.toPrettyString());
                statementArray.removeAll();
                errorNodeList.clear();
            }
            stack.push(statementArray);
        }
        else {
            final ArrayNode expr = stack.peek().addArray();
            stack.push(expr);
        }
    }

    private void exitNode(final ParserRuleContext ctx) {
        stack.pop();
    }

    @Override
    public void enterStatement(final DataPrepperStatementParser.StatementContext ctx) {
        super.enterStatement(ctx);
        enterNode(ctx);
    }

    @Override
    public void exitStatement(final DataPrepperStatementParser.StatementContext ctx) {
        super.exitStatement(ctx);
        exitNode(ctx);
    }

    @Override
    public void enterExpression(final DataPrepperStatementParser.ExpressionContext ctx) {
        super.enterExpression(ctx);
        enterNode(ctx);
    }

    @Override
    public void exitExpression(final DataPrepperStatementParser.ExpressionContext ctx) {
        super.exitExpression(ctx);
        exitNode(ctx);
    }

    @Override
    public void enterListInitializer(final DataPrepperStatementParser.ListInitializerContext ctx) {
        super.enterListInitializer(ctx);
        enterNode(ctx);
    }

    @Override
    public void exitListInitializer(final DataPrepperStatementParser.ListInitializerContext ctx) {
        super.exitListInitializer(ctx);
        exitNode(ctx);
    }

    @Override
    public void enterRegexPattern(final DataPrepperStatementParser.RegexPatternContext ctx) {
        super.enterRegexPattern(ctx);
        enterNode(ctx);
    }

    @Override
    public void exitRegexPattern(final DataPrepperStatementParser.RegexPatternContext ctx) {
        super.exitRegexPattern(ctx);
        exitNode(ctx);
    }

    @Override
    public void visitErrorNode(final ErrorNode node) {
        super.visitErrorNode(node);
        errorNodeList.add(node);
    }

    private void popVerboseTokens() {
        final String tokens = String.join(",", verboseTokenList);
        verboseTokenList.clear();
        verboseString += tokens;
    }

    @Override
    public void enterEveryRule(final ParserRuleContext ctx) {
        super.enterEveryRule(ctx);
        popVerboseTokens();
        verboseString += "[";
    }

    @Override
    public void exitEveryRule(final ParserRuleContext ctx) {
        super.exitEveryRule(ctx);
        popVerboseTokens();
        verboseString += "]";
    }

    /**
     * Formats JSON array to a less escaped syntax for testing. Strings will be surrounded by ' character instead of ".
     * Examples: "hello" -> 'hello'. "hello \"world\"" -> 'hello \"world\"'. An assertion statement for hello world
     * would be assertThat(helloWorldVal, is("'hello \\\"world\\\"'")).
     * @return formatted json string
     */
    @Override
    public String toString() {
        String simpleString = statementArray.toString()
                .replace("\\\"", ESCAPED_DOUBLE_QUOTE)
                .replace("\"", "'")
                .replace(ESCAPED_DOUBLE_QUOTE, "\\\"")
                .replace(ESCAPED_FORWARD_SLASH, "\\/")
                .replace(",'<EOF>'", "")
                .replace("'[',", "")
                .replace(",']'", "")
                .replace(",',',", ",");
        simpleString = simpleString.substring(1, simpleString.length() - 1);
        return simpleString;
    }

    public String toVerboseString() {
        return verboseString;
    }

    public List<ErrorNode> getErrorNodeList() {
        return errorNodeList;
    }

    public List<Exception> getExceptionList() {
        return exceptionList;
    }

    public String toPrettyString() {
        return statementArray.toPrettyString();
    }
}
