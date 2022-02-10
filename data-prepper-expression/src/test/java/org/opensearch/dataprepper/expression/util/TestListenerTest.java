/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.dataprepper.expression.util;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.expression.antlr.DataPrepperStatementParser;

import javax.annotation.Nullable;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestListenerTest {

    @Mock
    private ParserRuleContext parserRuleCtx;
    @Mock
    private DataPrepperStatementParser.StatementContext statementCtx;
    @Mock
    private DataPrepperStatementParser.ExpressionContext expressionCtx;
    @Mock
    private DataPrepperStatementParser.PrimaryContext primaryCtx;
    @Mock
    private DataPrepperStatementParser.LiteralContext literalCtx;
    @Mock
    private DataPrepperStatementParser.ListInitializerContext listInitializerCtx;
    @Mock
    private RecognitionException recognitionException;

    private TestListener listener;

    @BeforeEach
    void beforeEach() {
        listener = new TestListener();
    }

    private TerminalNode mockTerminalNode(final int type, @Nullable final String text) {
        final TerminalNode node = mock(TerminalNode.class);
        final Token symbol = mock(Token.class);

        when(symbol.getType())
                .thenReturn(type);
        if (text != null) {
            when(symbol.getText())
                    .thenReturn(text);
        }

        when(node.getSymbol())
                .thenReturn(symbol);

        return node;
    }

    private TerminalNode mockEndOfFileTerminalNode() {
        final TerminalNode node = mock(TerminalNode.class);
        final Token symbol = mock(Token.class);

        when(node.getSymbol())
                .thenReturn(symbol);
        when(symbol.getType())
                .thenReturn(DataPrepperStatementParser.EOF);
        when(symbol.getText())
                .thenReturn("<EOF>");

        return node;
    }

    private ErrorNode mockErrorNode() {
        final ErrorNode node = mock(ErrorNode.class);

        return node;
    }

    private Runnable inStatement(final Runnable callback) {
        return () -> {
            final TerminalNode eof = mockEndOfFileTerminalNode();

            listener.enterEveryRule(parserRuleCtx);
            listener.enterStatement(statementCtx);
            callback.run();
            listener.visitTerminal(eof);
            listener.exitStatement(statementCtx);
            listener.exitEveryRule(parserRuleCtx);
        };
    }

    private Runnable inExpression(final Runnable callback) {
        return () -> {
            listener.enterEveryRule(parserRuleCtx);
            listener.enterExpression(expressionCtx);
            callback.run();
            listener.exitExpression(expressionCtx);
            listener.exitEveryRule(parserRuleCtx);
        };
    }

    private Runnable inOperatorExpression(final Runnable lhs, final TerminalNode operator, final Runnable rhs) {
        return () -> {
            listener.enterEveryRule(parserRuleCtx);
            listener.enterExpression(expressionCtx);
            lhs.run();
            listener.visitTerminal(operator);
            rhs.run();
            listener.exitExpression(expressionCtx);
            listener.exitEveryRule(parserRuleCtx);
        };
    }

    private Runnable inPrimary(final Runnable callback) {
        return () -> {
            listener.enterEveryRule(parserRuleCtx);
            listener.enterPrimary(primaryCtx);
            callback.run();
            listener.exitPrimary(primaryCtx);
            listener.exitEveryRule(parserRuleCtx);
        };
    }

    private Runnable inLiteral(final Runnable callback) {
        return () -> {
            listener.enterEveryRule(parserRuleCtx);
            listener.enterLiteral(literalCtx);
            callback.run();
            listener.exitLiteral(literalCtx);
            listener.exitEveryRule(parserRuleCtx);
        };
    }

    private Runnable inListInitializer(final Runnable... callbacks) {
        return () -> {
            final TerminalNode lBrack = mockTerminalNode(DataPrepperStatementParser.LBRACK, "[");
            final TerminalNode rBrack = mockTerminalNode(DataPrepperStatementParser.RBRACK, "]");
            final TerminalNode listSeparator = mockTerminalNode(DataPrepperStatementParser.LISTSEPARATOR, ",");

            listener.enterEveryRule(parserRuleCtx);
            listener.enterListInitializer(listInitializerCtx);
            listener.visitTerminal(lBrack);

            for (int x = 0; x < callbacks.length; x++) {
                if (x > 0) {
                    listener.visitTerminal(listSeparator);
                }
                callbacks[x].run();
            }

            listener.visitTerminal(rBrack);
            listener.exitListInitializer(listInitializerCtx);
            listener.exitEveryRule(parserRuleCtx);
        };
    }

    private Runnable visitTerminal(final TerminalNode terminalNode) {
        return () -> listener.visitTerminal(terminalNode);
    }

    private Runnable visitErrorNode(final ErrorNode errorNode) {
        return () -> listener.visitErrorNode(errorNode);
    }

    private void setContextExceptions(final RecognitionException e) {
        parserRuleCtx.exception = e;
        statementCtx.exception = e;
        expressionCtx.exception = e;
        primaryCtx.exception = e;
        literalCtx.exception = e;
        listInitializerCtx.exception = e;
    }

    private Runnable withException(final Function<Runnable, Runnable> in, final Runnable callback) {
        return () -> {
            setContextExceptions(recognitionException);
            in.apply(() -> {
                setContextExceptions(null);
                callback.run();
                setContextExceptions(recognitionException);
            }).run();

            setContextExceptions(null);
        };
    }

    private void testToken(final String token, final int type) {
        testToken(token, token, type);
    }

    private void testToken(final String token, final String result, final int type) {
        final TerminalNode node = mockTerminalNode(type, token);
        inStatement(
                inExpression(
                        inPrimary(
                                inLiteral(visitTerminal(node))
                        )
                )
        ).run();

        assertAll(
                () -> assertThat(listener, ListenerMatcher.isValid()),
                () -> assertThat(listener.toString(), is("[" + result + "]")),
                () -> assertThat(listener.toVerboseString(), is("[[[[" + token + "]]]<EOF>]"))
        );
    }

    private void testOperator(final String operand, final int operandType, final String operator, final int operatorType) {
        final TerminalNode operandNode = mockTerminalNode(operandType, operand);
        final TerminalNode operatorNode = mockTerminalNode(operatorType, operator);
        inStatement(
                inOperatorExpression(
                         inPrimary(
                                inLiteral(visitTerminal(operandNode))),
                        operatorNode,
                         inPrimary(
                                inLiteral(visitTerminal(operandNode)))
                )
        ).run();

        assertAll(
                () -> assertThat(listener, ListenerMatcher.isValid()),
                () -> assertThat(listener.toString(), is("[" + operand + ",'" + operator + "'," + operand + "]")),
                () -> assertThat(listener.toVerboseString(), is("[[[[" + operand + "]]" + operator + "[[" + operand + "]]]<EOF>]"))
        );
    }

    @Test
    public void testIntegerToken() {
        testToken("5", DataPrepperStatementParser.Integer);
    }

    @Test
    public void testFloatToken() {
        testToken("3.1415", DataPrepperStatementParser.Float);
    }

    @Test
    public void testBooleanToken() {
        testToken("true", DataPrepperStatementParser.Boolean);
    }

    @Test
    public void testJsonPointerToken() {
        testToken("/people/1/name", "'/people/1/name'", DataPrepperStatementParser.JsonPointer);
    }

    @Test
    public void testEscapedJsonPointerToken() {
        testToken("\"/people are\\// probably /'~here~'\"", "'/people are\\// probably /'~here~''", DataPrepperStatementParser.JsonPointer);
    }

    @Test
    public void testStringToken() {
        testToken("\"Hello World\"", "'Hello World'", DataPrepperStatementParser.String);
    }

    @Test
    public void testBooleanLikeStringToken() {
        testToken("\"true\"", "'true'", DataPrepperStatementParser.String);
    }

    @Test
    public void testStringWithEscapeCharactersToken() {
        testToken("\"Hello \"World\"\"", "'Hello \\\"World\\\"'", DataPrepperStatementParser.String);
    }

    @Test
    public void testStringWithMultipleEscapeCharactersToken() {
        testToken("\"Hello \\\"World\\\"\"", "'Hello \\\"World\\\"'", DataPrepperStatementParser.String);
    }

    @Test
    public void testInvalidExpressionRule() {
        final ErrorNode node = mock(ErrorNode.class);

        inStatement(withException(this::inExpression, visitErrorNode(node)))
                .run();
        assertAll(
                () -> assertThat(listener, ListenerMatcher.hasError()),
                () -> assertThat(listener.toString(), is("[]")),
                () -> assertThat(listener.toVerboseString(), is("[[]<EOF>]"))
        );
    }

    @Test
    public void testListRule() {
        final TerminalNode integerNode = mockTerminalNode(DataPrepperStatementParser.Integer, "1");

        final Runnable expression = inExpression(
                inPrimary(
                        inLiteral(visitTerminal(integerNode))
                )
        );
        final Runnable[] listExpressions = new Runnable[]{expression, expression, expression};

        inStatement(
                inExpression(
                        inPrimary(
                                inListInitializer(listExpressions)
                        )
                )
        ).run();

        assertAll(
                () -> assertThat(listener, ListenerMatcher.isValid()),
                () -> assertThat(listener.toString(), is("[[[1],[1],[1]]]")),
                () -> assertThat(listener.toVerboseString(), is("[[[[[[[[1]]],[[[1]]],[[[1]]]]]]]<EOF>]"))
        );
    }

    @Test
    public void testEqualOperatorExpressionRule() {
        testOperator("5", DataPrepperStatementParser.Integer,
                "==", DataPrepperStatementParser.EQUAL);
    }

    @Test
    public void testNotEqualOperatorExpressionRule() {
        testOperator("5", DataPrepperStatementParser.Integer,
                "!=", DataPrepperStatementParser.NOT_EQUAL);
    }

    @Test
    public void testLessThanOperatorExpressionRule() {
        testOperator("5", DataPrepperStatementParser.Integer,
                "<", DataPrepperStatementParser.LT);
    }

    @Test
    public void testLessThanOrEqualOperatorExpressionRule() {
        testOperator("5", DataPrepperStatementParser.Integer,
                "<=", DataPrepperStatementParser.LTE);
    }

    @Test
    public void testGreaterThanOperatorExpressionRule() {
        testOperator("5", DataPrepperStatementParser.Integer,
                ">", DataPrepperStatementParser.GT);
    }

    @Test
    public void testGreaterThanOrEqualOperatorExpressionRule() {
        testOperator("5", DataPrepperStatementParser.Integer,
                ">=", DataPrepperStatementParser.GTE);
    }

    @Test
    public void testAndOperatorExpressionRule() {
        testOperator("true", DataPrepperStatementParser.Boolean,
                "and", DataPrepperStatementParser.AND);
    }

    @Test
    public void testOrOperatorExpressionRule() {
        testOperator("true", DataPrepperStatementParser.Boolean,
                "and", DataPrepperStatementParser.OR);
    }
}