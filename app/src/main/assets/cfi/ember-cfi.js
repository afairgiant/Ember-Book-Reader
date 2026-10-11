// Ember's entry points into foliate-js's epubcfi.js.
//
// CfiScript.kt concatenates epubcfi.js (with its `export` keywords removed) and this file
// inside one function, so foliate's joinIndir, fromRange, toRange and parse are plain
// local names here. The page is a chapter document rendered by Readium.

// Readium appends its own nodes to the end of <body>: one container per decoration group
// and a spacer that pads the last column. They are not part of the book.
const READIUM_NODES = '#readium-virtual-page, [data-group]'

const skipReadiumNodes = node =>
    node.nodeType === Node.ELEMENT_NODE &&
    node.parentNode === document.body &&
    node.matches(READIUM_NODES)
        ? NodeFilter.FILTER_REJECT
        : NodeFilter.FILTER_ACCEPT

const cfiFor = (base, range) => joinIndir(base, fromRange(range, skipReadiumNodes))

// The first text position on screen. Probes down the left edge and the middle of the
// viewport, because the corner itself is usually page margin.
const visibleStartRange = () => {
    for (let y = 1; y < window.innerHeight; y += 12) {
        for (const x of [window.innerWidth * 0.08, window.innerWidth * 0.5]) {
            const range = document.caretRangeFromPoint(x, y)
            const node = range?.startContainer
            if (
                node?.nodeType === Node.TEXT_NODE &&
                node.nodeValue.trim() &&
                !node.parentElement?.closest(READIUM_NODES)
            ) {
                range.collapse(true)
                return range
            }
        }
    }
    return null
}

// Up to 64 characters of chapter text just before or just after the range.
const contextOf = (range, before) => {
    const context = document.createRange()
    context.selectNodeContents(document.body)
    const readiumNode = [...document.body.children].find(el => el.matches(READIUM_NODES))
    if (readiumNode) context.setEndBefore(readiumNode)
    if (before) context.setEnd(range.startContainer, range.startOffset)
    else context.setStart(range.endContainer, range.endOffset)
    const text = context.toString()
    return before ? text.slice(-64) : text.slice(0, 64)
}

window.emberCfi = {
    // CFI of the current text selection, or null when nothing is selected.
    forSelection(base) {
        const selection = window.getSelection()
        if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null
        return cfiFor(base, selection.getRangeAt(0))
    },

    // CFI of the first text position on screen, or null when none is found.
    forVisibleStart(base) {
        const range = visibleStartRange()
        return range ? cfiFor(base, range) : null
    },

    // The text a CFI in this chapter points at, with context on both sides. The caller
    // checks that the CFI's base belongs to this chapter.
    resolve(cfi) {
        const parts = parse(cfi)
        ;(parts.parent ?? parts).shift()
        const range = toRange(document, parts, skipReadiumNodes)
        return {
            exact: range.toString(),
            before: contextOf(range, true),
            after: contextOf(range, false),
        }
    },
}
