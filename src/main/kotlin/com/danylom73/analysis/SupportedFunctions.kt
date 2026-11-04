package com.danylom73.analysis

data class FunctionDefinition(
    val name: String,
    val argumentCount: Int,
)

object SupportedFunctions {
    private val definitions = listOf(
        FunctionDefinition("sin", 1),
        FunctionDefinition("cos", 1),
        FunctionDefinition("tan", 1),
        FunctionDefinition("ctg", 1),
        FunctionDefinition("sqrt", 1),
        FunctionDefinition("abs", 1),
        FunctionDefinition("round", 1),
        FunctionDefinition("ln", 1),
        FunctionDefinition("log10", 1),
        FunctionDefinition("pow", 2),
        FunctionDefinition("min", 2),
        FunctionDefinition("max", 2),
    ).associateBy(FunctionDefinition::name)

    fun find(name: String): FunctionDefinition? = definitions[name]

    fun all(): List<FunctionDefinition> = definitions.values.toList()
}
