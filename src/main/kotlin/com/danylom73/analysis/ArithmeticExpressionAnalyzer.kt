package com.danylom73.analysis

class ArithmeticExpressionAnalyzer(
    private val lexer: Lexer = Lexer(),
    private val syntaxAnalyzer: FiniteStateSyntaxAnalyzer = FiniteStateSyntaxAnalyzer(),
) {
    fun analyze(expression: String): AnalysisResult {
        val lexicalResult = lexer.tokenize(expression)
        val syntaxErrors = syntaxAnalyzer.analyze(lexicalResult.tokens)
        val allErrors = (lexicalResult.errors + syntaxErrors)
            .sortedWith(compareBy(AnalysisError::offset, AnalysisError::category, AnalysisError::code))

        return AnalysisResult(
            tokens = lexicalResult.tokens.filter { it.type != TokenType.END_OF_INPUT },
            errors = allErrors,
        )
    }
}
