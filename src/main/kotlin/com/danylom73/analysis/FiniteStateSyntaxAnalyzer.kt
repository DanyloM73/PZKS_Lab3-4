package com.danylom73.analysis

import java.util.ArrayDeque

class FiniteStateSyntaxAnalyzer {
    private enum class State {
        START,
        OPERAND,
        FUNCTION,
        BINARY_OPERATOR,
        UNARY_OPERATOR,
        LEFT_PARENTHESIS,
        COMMA,
        RIGHT_PARENTHESIS,
        END,
    }

    private enum class Action {
        READ_OPERAND,
        READ_FUNCTION,
        READ_BINARY_OPERATOR,
        READ_UNARY_OPERATOR,
        OPEN_PARENTHESIS,
        CLOSE_PARENTHESIS,
        SEPARATE_ARGUMENTS,
        FINISH,
    }

    private data class Transition(
        val nextState: State,
        val action: Action,
    )

    private data class ParenthesisFrame(
        val opening: Token,
        val function: Token? = null,
        var commaCount: Int = 0,
        var hasMissingArgument: Boolean = false,
    )

    private data class Context(
        var state: State = State.START,
        var lastToken: Token? = null,
        val parentheses: ArrayDeque<ParenthesisFrame> = ArrayDeque(),
        val errors: MutableList<AnalysisError> = mutableListOf(),
    )

    private val transitionTable = mapOf(
        State.START to transitionMap(
            transition(State.OPERAND, Action.READ_OPERAND, TokenType.NUMBER, TokenType.IDENTIFIER),
            transition(State.FUNCTION, Action.READ_FUNCTION, TokenType.FUNCTION, TokenType.UNKNOWN_FUNCTION),
            transition(State.UNARY_OPERATOR, Action.READ_UNARY_OPERATOR, TokenType.PLUS, TokenType.MINUS),
            transition(State.LEFT_PARENTHESIS, Action.OPEN_PARENTHESIS, TokenType.LEFT_PARENTHESIS),
            transition(State.END, Action.FINISH, TokenType.END_OF_INPUT),
        ),
        State.OPERAND to completedValueTransitions(),
        State.FUNCTION to transitionMap(
            transition(State.LEFT_PARENTHESIS, Action.OPEN_PARENTHESIS, TokenType.LEFT_PARENTHESIS),
        ),
        State.BINARY_OPERATOR to operandBeginningTransitions(),
        State.UNARY_OPERATOR to operandBeginningTransitions(),
        State.LEFT_PARENTHESIS to transitionMap(
            transition(State.OPERAND, Action.READ_OPERAND, TokenType.NUMBER, TokenType.IDENTIFIER),
            transition(State.FUNCTION, Action.READ_FUNCTION, TokenType.FUNCTION, TokenType.UNKNOWN_FUNCTION),
            transition(State.UNARY_OPERATOR, Action.READ_UNARY_OPERATOR, TokenType.PLUS, TokenType.MINUS),
            transition(State.LEFT_PARENTHESIS, Action.OPEN_PARENTHESIS, TokenType.LEFT_PARENTHESIS),
        ),
        State.COMMA to transitionMap(
            transition(State.OPERAND, Action.READ_OPERAND, TokenType.NUMBER, TokenType.IDENTIFIER),
            transition(State.FUNCTION, Action.READ_FUNCTION, TokenType.FUNCTION, TokenType.UNKNOWN_FUNCTION),
            transition(State.UNARY_OPERATOR, Action.READ_UNARY_OPERATOR, TokenType.PLUS, TokenType.MINUS),
            transition(State.LEFT_PARENTHESIS, Action.OPEN_PARENTHESIS, TokenType.LEFT_PARENTHESIS),
        ),
        State.RIGHT_PARENTHESIS to completedValueTransitions(),
        State.END to emptyMap(),
    )

    fun analyze(tokens: List<Token>): List<AnalysisError> {
        val context = Context()
        val significantTokens = tokens.filter { it.type != TokenType.END_OF_INPUT }

        if (significantTokens.isEmpty()) {
            val end = tokens.last()
            return listOf(end.syntaxError(ErrorCode.EMPTY_EXPRESSION, "Арифметичний вираз порожній."))
        }

        tokens.forEach { token -> consume(token, context) }

        while (context.parentheses.isNotEmpty()) {
            val opening = context.parentheses.removeLast().opening
            context.errors += opening.syntaxError(
                ErrorCode.UNMATCHED_OPENING_PARENTHESIS,
                "Відкривальна дужка не має відповідної закривальної дужки.",
            )
        }

        return context.errors
    }

    private fun consume(token: Token, context: Context) {
        while (context.state != State.END) {
            val syntaxType = token.syntaxType()
            val transition = syntaxType
                ?.let { transitionTable[context.state]?.get(it) }
                ?.takeUnless {
                    it.action == Action.READ_UNARY_OPERATOR && context.lastToken?.type == TokenType.INVALID
                }

            if (transition != null) {
                applyTransition(transition, token, context)
                return
            }

            if (context.state == State.FUNCTION) {
                context.errors += functionParenthesesError(requireNotNull(context.lastToken))
                context.state = State.OPERAND
                continue
            }

            handleInvalidTransition(token, context)
            return
        }
    }

    private fun applyTransition(transition: Transition, token: Token, context: Context) {
        val sourceState = context.state

        when (transition.action) {
            Action.OPEN_PARENTHESIS -> {
                val function = context.lastToken.takeIf { sourceState == State.FUNCTION }
                context.parentheses.push(ParenthesisFrame(token, function))
            }

            Action.CLOSE_PARENTHESIS -> closeParenthesis(token, sourceState, context)
            Action.SEPARATE_ARGUMENTS -> separateArguments(token, sourceState, context)
            Action.READ_OPERAND,
            Action.READ_FUNCTION,
            Action.READ_BINARY_OPERATOR,
            Action.READ_UNARY_OPERATOR,
            Action.FINISH,
            -> Unit
        }

        context.state = transition.nextState
        context.lastToken = token
    }

    private fun handleInvalidTransition(token: Token, context: Context) {
        when (token.syntaxType()) {
            TokenType.NUMBER, TokenType.IDENTIFIER -> {
                context.errors += missingOperatorError(token, context.lastToken)
                context.state = State.OPERAND
            }

            TokenType.FUNCTION, TokenType.UNKNOWN_FUNCTION -> {
                context.errors += token.syntaxError(
                    ErrorCode.MISSING_OPERATOR,
                    "Перед функцією «${token.lexeme}» відсутня операція.",
                )
                context.state = State.FUNCTION
            }

            TokenType.LEFT_PARENTHESIS -> {
                context.errors += token.syntaxError(
                    ErrorCode.MISSING_OPERATOR,
                    "Перед відкритою дужкою відсутня операція.",
                )
                context.parentheses.push(ParenthesisFrame(token))
                context.state = State.LEFT_PARENTHESIS
            }

            TokenType.RIGHT_PARENTHESIS -> {
                closeParenthesis(token, context.state, context)
                context.state = State.RIGHT_PARENTHESIS
            }

            TokenType.COMMA -> {
                separateArguments(token, context.state, context)
                context.state = State.COMMA
            }

            TokenType.PLUS, TokenType.MINUS, TokenType.MULTIPLY, TokenType.DIVIDE -> {
                handleInvalidOperator(token, context)
                context.state = State.BINARY_OPERATOR
            }

            TokenType.END_OF_INPUT -> {
                reportInvalidEnd(context)
                context.state = State.END
            }

            TokenType.INVALID, null -> Unit
        }

        context.lastToken = token
    }

    private fun handleInvalidOperator(token: Token, context: Context) {
        when (context.state) {
            State.START if context.lastToken == null -> context.errors += token.syntaxError(
                ErrorCode.INVALID_START,
                "Вираз не може починатися з операції «${token.lexeme}».",
            )

            State.LEFT_PARENTHESIS if context.lastToken?.type == TokenType.LEFT_PARENTHESIS ->
                context.errors += token.syntaxError(
                ErrorCode.OPERATOR_AFTER_OPENING_PARENTHESIS,
                "Операція «${token.lexeme}» не може стояти одразу після відкритої дужки.",
            )

            State.COMMA if context.lastToken?.type == TokenType.COMMA -> {
                val frame = context.parentheses.peek()
                context.errors += token.syntaxError(
                    ErrorCode.MISSING_FUNCTION_ARGUMENT,
                    "Аргумент функції не може починатися з операції «${token.lexeme}».",
                )
                frame?.hasMissingArgument = true
            }

            else -> context.errors += token.syntaxError(
                ErrorCode.DOUBLE_OPERATOR,
                "Подвійна операція «${context.lastToken?.lexeme}${token.lexeme}» не дозволена.",
            )
        }
    }

    private fun separateArguments(token: Token, sourceState: State, context: Context) {
        val frame = context.parentheses.peek()
        if (frame?.function == null) {
            context.errors += token.syntaxError(
                ErrorCode.COMMA_OUTSIDE_FUNCTION,
                "Кома дозволена лише між аргументами функції.",
            )
            return
        }

        if (sourceState != State.OPERAND && sourceState != State.RIGHT_PARENTHESIS) {
            context.errors += token.syntaxError(
                ErrorCode.MISSING_FUNCTION_ARGUMENT,
                "Перед комою відсутній аргумент функції «${frame.function.lexeme}».",
            )
            frame.hasMissingArgument = true
        }
        frame.commaCount++
    }

    private fun closeParenthesis(token: Token, sourceState: State, context: Context) {
        val frame = context.parentheses.peek()
        val isEmpty = sourceState == State.LEFT_PARENTHESIS &&
            context.lastToken?.type == TokenType.LEFT_PARENTHESIS
        val immediatelyAfterComma = sourceState == State.COMMA &&
            context.lastToken?.type == TokenType.COMMA

        when {
            sourceState == State.START && context.lastToken == null -> context.errors += token.syntaxError(
                ErrorCode.INVALID_START,
                "Вираз не може починатися із закритої дужки.",
            )

            isEmpty && frame?.function != null -> {
                context.errors += token.syntaxError(
                    ErrorCode.EMPTY_FUNCTION_ARGUMENT,
                    "Функція «${frame.function.lexeme}» не може мати порожній список аргументів.",
                )
                frame.hasMissingArgument = true
            }

            isEmpty -> context.errors += token.syntaxError(
                ErrorCode.EMPTY_PARENTHESES,
                "Порожні дужки не дозволені.",
            )

            immediatelyAfterComma && frame?.function != null -> {
                context.errors += token.syntaxError(
                    ErrorCode.MISSING_FUNCTION_ARGUMENT,
                    "Після коми відсутній аргумент функції «${frame.function.lexeme}».",
                )
                frame.hasMissingArgument = true
            }

            sourceState in setOf(
                State.BINARY_OPERATOR,
                State.UNARY_OPERATOR,
                State.LEFT_PARENTHESIS,
                State.COMMA,
                State.START,
            ) -> context.errors += token.syntaxError(
                ErrorCode.MISSING_OPERAND,
                "Перед закритою дужкою відсутній операнд.",
            )
        }

        if (frame == null) {
            context.errors += token.syntaxError(
                ErrorCode.UNMATCHED_CLOSING_PARENTHESIS,
                "Закривальна дужка не має відповідної відкривальної дужки.",
            )
        } else {
            context.parentheses.pop()
            validateArgumentCount(frame, isEmpty, context.errors)
        }
    }

    private fun reportInvalidEnd(context: Context) {
        val last = requireNotNull(context.lastToken)
        val message = if (context.state == State.COMMA) {
            "Вираз не може закінчуватися комою."
        } else {
            "Вираз не може закінчуватися операцією «${last.lexeme}» або відкритою дужкою."
        }
        context.errors += last.syntaxError(ErrorCode.INVALID_END, message)
    }

    private fun validateArgumentCount(
        frame: ParenthesisFrame,
        isEmpty: Boolean,
        errors: MutableList<AnalysisError>,
    ) {
        val function = frame.function ?: return
        val definition = SupportedFunctions.find(function.lexeme) ?: return
        if (isEmpty || frame.hasMissingArgument) return

        val actualCount = frame.commaCount + 1
        if (actualCount != definition.argumentCount) {
            errors += function.syntaxError(
                ErrorCode.WRONG_ARGUMENT_COUNT,
                "Функція «${function.lexeme}» очікує ${definition.argumentCount} арг., отримано $actualCount.",
            )
        }
    }

    private fun missingOperatorError(token: Token, previous: Token?): AnalysisError {
        val message = if (previous?.type == TokenType.RIGHT_PARENTHESIS) {
            "Відсутня операція між закритою дужкою та «${token.lexeme}»."
        } else {
            "Відсутня операція між «${previous?.lexeme}» та «${token.lexeme}»."
        }
        return token.syntaxError(ErrorCode.MISSING_OPERATOR, message)
    }

    private fun functionParenthesesError(function: Token) = function.syntaxError(
        ErrorCode.FUNCTION_PARENTHESES_REQUIRED,
        "Після назви функції «${function.lexeme}» очікується відкривальна дужка.",
    )

    private fun Token.syntaxType(): TokenType? = when {
        type != TokenType.INVALID -> type
        lexeme.firstOrNull()?.let { it.isLetterOrDigit() || it == '_' || it == '.' } == true -> TokenType.IDENTIFIER
        else -> null
    }

    private fun completedValueTransitions() = transitionMap(
        transition(
            State.BINARY_OPERATOR,
            Action.READ_BINARY_OPERATOR,
            TokenType.PLUS,
            TokenType.MINUS,
            TokenType.MULTIPLY,
            TokenType.DIVIDE,
        ),
        transition(State.RIGHT_PARENTHESIS, Action.CLOSE_PARENTHESIS, TokenType.RIGHT_PARENTHESIS),
        transition(State.COMMA, Action.SEPARATE_ARGUMENTS, TokenType.COMMA),
        transition(State.END, Action.FINISH, TokenType.END_OF_INPUT),
    )

    private fun operandBeginningTransitions() = transitionMap(
        transition(State.OPERAND, Action.READ_OPERAND, TokenType.NUMBER, TokenType.IDENTIFIER),
        transition(State.FUNCTION, Action.READ_FUNCTION, TokenType.FUNCTION, TokenType.UNKNOWN_FUNCTION),
        transition(State.LEFT_PARENTHESIS, Action.OPEN_PARENTHESIS, TokenType.LEFT_PARENTHESIS),
    )

    private fun transition(
        nextState: State,
        action: Action,
        vararg tokenTypes: TokenType,
    ): List<Pair<TokenType, Transition>> = tokenTypes.map { it to Transition(nextState, action) }

    private fun transitionMap(
        vararg transitions: List<Pair<TokenType, Transition>>,
    ): Map<TokenType, Transition> = transitions.flatMap { it }.toMap()

    private fun Token.syntaxError(code: ErrorCode, message: String) = AnalysisError(
        ErrorCategory.SYNTACTIC,
        code,
        message,
        offset,
        line,
        column,
        lexeme.length.coerceAtLeast(1),
    )
}
