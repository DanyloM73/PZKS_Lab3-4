package com.danylom73.expression

import com.danylom73.analysis.Token
import com.danylom73.analysis.TokenType
import java.util.IdentityHashMap

data class SourceSpan(val start: Token, val endOffset: Int) {
    val length: Int
        get() = endOffset - start.offset
}

class ExpressionParser(private val tokens: List<Token>) {
    private var position = 0
    val sourceSpans: Map<Expression, SourceSpan>
        field = IdentityHashMap<Expression, SourceSpan>()

    fun parse(): Expression {
        val expression = parseExpression(0)
        require(position == tokens.size) { "Неочікуваний токен: ${tokens[position].lexeme}" }
        return expression
    }

    private fun parseExpression(minimumPrecedence: Int): Expression {
        var left = parsePrefix()
        while (true) {
            val token = tokens.getOrNull(position) ?: break
            val precedence = precedence(token.type) ?: break
            if (precedence < minimumPrecedence) break
            position++
            val right = parseExpression(precedence + 1)
            left = remember(Expression.Binary(token.lexeme, left, right), sourceSpans.getValue(left).start, sourceSpans.getValue(right).endOffset)
        }
        return left
    }

    private fun parsePrefix(): Expression {
        val token = tokens.getOrNull(position++) ?: error("Неочікуваний кінець виразу")
        return when (token.type) {
            TokenType.NUMBER, TokenType.IDENTIFIER -> remember(Expression.Value(token.lexeme), token, token.end)
            TokenType.PLUS, TokenType.MINUS -> {
                val operand = parseExpression(30)
                remember(Expression.Unary(token.lexeme, operand), token, sourceSpans.getValue(operand).endOffset)
            }
            TokenType.LEFT_PARENTHESIS -> {
                val inner = parseExpression(0)
                remember(inner, token, expect(TokenType.RIGHT_PARENTHESIS).end)
            }
            TokenType.FUNCTION -> {
                expect(TokenType.LEFT_PARENTHESIS)
                val arguments = mutableListOf(parseExpression(0))
                while (tokens.getOrNull(position)?.type == TokenType.COMMA) {
                    position++
                    arguments += parseExpression(0)
                }
                remember(Expression.Call(token.lexeme, arguments), token, expect(TokenType.RIGHT_PARENTHESIS).end)
            }
            else -> error("Неочікуваний токен: ${token.lexeme}")
        }
    }

    private fun <T : Expression> remember(node: T, start: Token, endOffset: Int): T =
        node.also { sourceSpans[it] = SourceSpan(start, endOffset) }

    private fun expect(type: TokenType): Token {
        val token = requireNotNull(tokens.getOrNull(position)?.takeIf { it.type == type }) { "Очікувався токен $type" }
        position++
        return token
    }

    private fun precedence(type: TokenType): Int? = when (type) {
        TokenType.PLUS, TokenType.MINUS -> 10
        TokenType.MULTIPLY, TokenType.DIVIDE -> 20
        else -> null
    }

    private val Token.end: Int
        get() = offset + lexeme.length
}
