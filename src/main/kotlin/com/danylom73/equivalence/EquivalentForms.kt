package com.danylom73.equivalence

import com.danylom73.expression.Expression
import com.danylom73.parallel.ParallelPlan
import java.math.BigInteger

enum class AlgebraicLaw(val title: String, val statement: String) {
    COMMUTATIVE("комутативний закон", "a + b = b + a, a * b = b * a"),
    DISTRIBUTIVE("дистрибутивний закон", "a * (b + c) = a * b + a * c"),
}

data class FormOrigin(
    val parentId: Int,
    val description: String,
    val before: Form,
    val after: Form,
)

class EquivalentForm(
    val id: Int,
    val form: Form,
    val origin: FormOrigin? = null,
    private val layout: Form = form,
) {
    val text: String = FormFormatter.format(form)
    val depth: Int
    val operations: Int
    val maximumWidth: Int

    init {
        val plan = plan()
        depth = plan.depth
        operations = plan.operations.size
        maximumWidth = plan.maximumWidth
    }

    fun tree(): Expression = ParallelTreeBuilder.build(layout)

    fun plan(): ParallelPlan = ParallelPlan.from(tree())
}

class EquivalenceResult(
    val law: AlgebraicLaw,
    val forms: List<EquivalentForm>,
    val total: BigInteger?,
) {
    val original: EquivalentForm
        get() = forms.first()

    val complete: Boolean
        get() = total == forms.size.toBigInteger()

    val ranked: List<EquivalentForm> by lazy { forms.sortedWith(QUALITY) }

    val best: EquivalentForm
        get() = ranked.first()

    val bestCount: Int
        get() = forms.count(::isBest)

    fun isBest(form: EquivalentForm): Boolean = form.depth == best.depth &&
        form.operations == best.operations && form.maximumWidth == best.maximumWidth

    fun find(id: Int): EquivalentForm? = forms.getOrNull(id - 1)

    companion object {
        val QUALITY: Comparator<EquivalentForm> = compareBy(EquivalentForm::depth, EquivalentForm::operations)
            .thenByDescending(EquivalentForm::maximumWidth)
            .thenBy(EquivalentForm::id)
    }
}

object EquivalentForms {
    const val COMMUTATIVE_LIMIT = 50_000
    const val DISTRIBUTIVE_LIMIT = 500

    fun commutative(expression: Expression): EquivalenceResult {
        val original = Forms.from(expression)
        val forms = mutableListOf(EquivalentForm(1, original))
        val arranged = CommutativeLaw.arrange(original)
        if (arranged != original) {
            forms += EquivalentForm(2, arranged, FormOrigin(1, "сортування термів за вагою", original, arranged))
        }
        CommutativeLaw.variants(original, COMMUTATIVE_LIMIT)
            .filter { it != original && it != arranged }
            .take(COMMUTATIVE_LIMIT - forms.size)
            .forEach { forms += EquivalentForm(forms.size + 1, it) }
        val extra = if (CommutativeLaw.isArranged(original)) BigInteger.ZERO else BigInteger.ONE
        return EquivalenceResult(AlgebraicLaw.COMMUTATIVE, forms, CommutativeLaw.count(original) + extra)
    }

    fun distributive(expression: Expression): EquivalenceResult {
        val original = Forms.from(expression)
        val forms = mutableListOf(EquivalentForm(1, original, layout = CommutativeLaw.arrange(original)))
        val seen = hashSetOf(Forms.canonical(forms.first().form))
        var next = 0
        var exhausted = true
        while (next < forms.size) {
            val current = forms[next++]
            for ((form, rule, before, after) in DistributiveLaw.rewrites(current.form)) {
                if (!seen.add(Forms.canonical(form))) continue
                if (forms.size == DISTRIBUTIVE_LIMIT) {
                    exhausted = false
                    break
                }
                val origin = FormOrigin(current.id, rule.description, before, after)
                forms += EquivalentForm(forms.size + 1, form, origin, CommutativeLaw.arrange(form))
            }
            if (!exhausted) break
        }
        return EquivalenceResult(AlgebraicLaw.DISTRIBUTIVE, forms, if (exhausted) forms.size.toBigInteger() else null)
    }
}
