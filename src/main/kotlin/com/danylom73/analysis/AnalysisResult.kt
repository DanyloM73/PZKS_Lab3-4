package com.danylom73.analysis

enum class ErrorCategory(val displayName: String) {
    LEXICAL("лексична"),
    SYNTACTIC("синтаксична"),
    SEMANTIC("семантична"),
}

enum class ErrorCode {
    EMPTY_EXPRESSION,
    UNKNOWN_SYMBOL,
    INVALID_IDENTIFIER,
    INVALID_NUMBER,
    UNKNOWN_FUNCTION,
    INVALID_START,
    INVALID_END,
    DOUBLE_OPERATOR,
    OPERATOR_AFTER_OPENING_PARENTHESIS,
    MISSING_OPERATOR,
    MISSING_OPERAND,
    EMPTY_PARENTHESES,
    UNMATCHED_OPENING_PARENTHESIS,
    UNMATCHED_CLOSING_PARENTHESIS,
    FUNCTION_PARENTHESES_REQUIRED,
    EMPTY_FUNCTION_ARGUMENT,
    MISSING_FUNCTION_ARGUMENT,
    WRONG_ARGUMENT_COUNT,
    COMMA_OUTSIDE_FUNCTION,
    DIVISION_BY_ZERO,
    INVALID_FUNCTION_ARGUMENT,
}

data class AnalysisError(
    val category: ErrorCategory,
    val code: ErrorCode,
    val message: String,
    val offset: Int,
    val line: Int,
    val column: Int,
    val length: Int = 1,
)

data class AnalysisResult(
    val tokens: List<Token>,
    val errors: List<AnalysisError>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}
