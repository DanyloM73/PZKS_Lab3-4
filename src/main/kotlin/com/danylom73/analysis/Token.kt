package com.danylom73.analysis

enum class TokenType {
    NUMBER,
    IDENTIFIER,
    FUNCTION,
    UNKNOWN_FUNCTION,
    PLUS,
    MINUS,
    MULTIPLY,
    DIVIDE,
    LEFT_PARENTHESIS,
    RIGHT_PARENTHESIS,
    COMMA,
    INVALID,
    END_OF_INPUT,
}

data class Token(
    val type: TokenType,
    val lexeme: String,
    val offset: Int,
    val line: Int,
    val column: Int,
)
