package com.danylom73.analysis

class Lexer {
    fun tokenize(source: String): LexicalResult {
        val tokens = mutableListOf<Token>()
        val errors = mutableListOf<AnalysisError>()
        var offset = 0
        var line = 1
        var column = 1

        fun addSimpleToken(type: TokenType) {
            tokens += Token(type, source[offset].toString(), offset, line, column)
            offset++
            column++
        }

        while (offset < source.length) {
            val current = source[offset]

            when {
                current == '\n' -> {
                    offset++
                    line++
                    column = 1
                }

                current.isWhitespace() -> {
                    offset++
                    column++
                }

                current.isLetter() || current == '_' -> {
                    val startOffset = offset
                    val startColumn = column
                    while (offset < source.length && source[offset].isIdentifierPart()) {
                        offset++
                        column++
                    }
                    val lexeme = source.substring(startOffset, offset)
                    var nextSignificantOffset = offset
                    while (source.getOrNull(nextSignificantOffset)?.isWhitespace() == true) {
                        nextSignificantOffset++
                    }
                    val followedByParenthesis = source.getOrNull(nextSignificantOffset) == '('
                    val knownFunction = SupportedFunctions.find(lexeme) != null
                    val tokenType = when {
                        knownFunction -> TokenType.FUNCTION
                        followedByParenthesis -> TokenType.UNKNOWN_FUNCTION
                        else -> TokenType.IDENTIFIER
                    }

                    tokens += Token(
                        tokenType,
                        lexeme,
                        startOffset,
                        line,
                        startColumn,
                    )
                    if (tokenType == TokenType.UNKNOWN_FUNCTION) {
                        errors += AnalysisError(
                            ErrorCategory.LEXICAL,
                            ErrorCode.UNKNOWN_FUNCTION,
                            "Невідома функція «$lexeme».",
                            startOffset,
                            line,
                            startColumn,
                            lexeme.length,
                        )
                    }
                }

                current.isDigit() || (current == '.' && source.getOrNull(offset + 1)?.isDigit() == true) -> {
                    val startOffset = offset
                    val startColumn = column
                    val tokenLine = line
                    var malformed = false

                    while (source.getOrNull(offset)?.isDigit() == true) {
                        offset++
                        column++
                    }

                    if (source.getOrNull(offset) == '.') {
                        offset++
                        column++
                        val fractionStart = offset
                        while (source.getOrNull(offset)?.isDigit() == true) {
                            offset++
                            column++
                        }
                        if (offset == fractionStart) malformed = true
                    }

                    if (source.getOrNull(offset) == 'e' || source.getOrNull(offset) == 'E') {
                        offset++
                        column++
                        if (source.getOrNull(offset) == '+' || source.getOrNull(offset) == '-') {
                            offset++
                            column++
                        }
                        val exponentStart = offset
                        while (source.getOrNull(offset)?.isDigit() == true) {
                            offset++
                            column++
                        }
                        if (offset == exponentStart) malformed = true
                    }

                    if (source.getOrNull(offset)?.let { it.isIdentifierPart() || it == '.' } == true) {
                        malformed = true
                        while (source.getOrNull(offset)?.let { it.isIdentifierPart() || it == '.' } == true) {
                            offset++
                            column++
                        }
                    }

                    val lexeme = source.substring(startOffset, offset)
                    if (malformed) {
                        val code = if (lexeme.any { it.isLetter() && it != 'e' && it != 'E' }) {
                            ErrorCode.INVALID_IDENTIFIER
                        } else {
                            ErrorCode.INVALID_NUMBER
                        }
                        val message = if (code == ErrorCode.INVALID_IDENTIFIER) {
                            "Некоректне ім'я змінної або константа «$lexeme»: ім'я не може починатися з цифри."
                        } else {
                            "Некоректно записана числова константа «$lexeme»."
                        }
                        tokens += Token(TokenType.INVALID, lexeme, startOffset, tokenLine, startColumn)
                        errors += AnalysisError(
                            ErrorCategory.LEXICAL,
                            code,
                            message,
                            startOffset,
                            tokenLine,
                            startColumn,
                            lexeme.length,
                        )
                    } else {
                        tokens += Token(TokenType.NUMBER, lexeme, startOffset, tokenLine, startColumn)
                    }
                }

                current == '+' -> addSimpleToken(TokenType.PLUS)
                current == '-' -> addSimpleToken(TokenType.MINUS)
                current == '*' -> addSimpleToken(TokenType.MULTIPLY)
                current == '/' -> addSimpleToken(TokenType.DIVIDE)
                current == '(' -> addSimpleToken(TokenType.LEFT_PARENTHESIS)
                current == ')' -> addSimpleToken(TokenType.RIGHT_PARENTHESIS)
                current == ',' -> addSimpleToken(TokenType.COMMA)

                else -> {
                    tokens += Token(TokenType.INVALID, current.toString(), offset, line, column)
                    errors += AnalysisError(
                        ErrorCategory.LEXICAL,
                        ErrorCode.UNKNOWN_SYMBOL,
                        "Невідомий символ «$current».",
                        offset,
                        line,
                        column,
                        1,
                    )
                    offset++
                    column++
                }
            }
        }

        tokens += Token(TokenType.END_OF_INPUT, "", offset, line, column)
        return LexicalResult(tokens, errors)
    }

    private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_'
}

data class LexicalResult(
    val tokens: List<Token>,
    val errors: List<AnalysisError>,
)
