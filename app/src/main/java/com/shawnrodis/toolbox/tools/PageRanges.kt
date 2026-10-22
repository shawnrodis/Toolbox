package com.shawnrodis.toolbox.tools

/**
 * Split page-selection maths, ported from the source's
 * pdfOnSplitRangeInput() + togglePageSelection() + processSplit().
 *
 * Semantics preserved:
 *  - Range text is comma-separated; each part is either "a-b" (inclusive)
 *    or a single page number. Parts that don't parse are ignored.
 *  - Pages are 1-based and clamped to 1..numPages.
 *  - Selection behaves like the source's JS Set: first-occurrence
 *    (insertion) order is kept and duplicates collapse. processSplit
 *    extracts in that Set order.
 *  - togglePageSelection rewrites the range field as the SORTED
 *    selection joined with ", " — while the underlying set keeps its
 *    insertion order. Both behaviours are kept separate here, as in the
 *    source.
 *  - An empty selection means "all pages" at process time.
 */
object PageRanges {

    /** pdfOnSplitRangeInput(val): parse range text into an ordered set. */
    fun parseRangeInput(input: String, numPages: Int): LinkedHashSet<Int> {
        val out = linkedSetOf<Int>()
        if (input.isBlank() || numPages <= 0) return out
        for (part in input.split(",")) {
            val p = part.trim()
            if (p.contains("-")) {
                val pieces = p.split("-")
                val s = pieces.getOrNull(0)?.trim()?.toIntOrNull()
                val e = pieces.getOrNull(1)?.trim()?.toIntOrNull()
                if (s != null && e != null) {
                    var i = s
                    while (i <= e) {
                        if (i in 1..numPages) out.add(i)
                        i++
                    }
                }
            } else {
                val v = p.toIntOrNull()
                if (v != null && v in 1..numPages) out.add(v)
            }
        }
        return out
    }

    /** togglePageSelection(): add if absent, remove if present (in place). */
    fun toggle(selection: LinkedHashSet<Int>, page: Int) {
        if (!selection.add(page)) selection.remove(page)
    }

    /** The range-field text after a thumbnail toggle: sorted, ", "-joined. */
    fun rangeFieldText(selection: Set<Int>): String =
        selection.sorted().joinToString(", ")

    /**
     * processSplit(): 0-based indices in selection (insertion) order;
     * empty selection -> every page in order.
     */
    fun splitIndices(selection: Set<Int>, totalPages: Int): List<Int> =
        if (selection.isEmpty()) List(totalPages) { it } else selection.map { it - 1 }
}
