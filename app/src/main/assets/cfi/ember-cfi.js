// Ember's entry points into foliate-js's epubcfi.js.
//
// CfiScript.kt concatenates epubcfi.js (with its `export` keywords removed) and this file
// inside one function, so foliate's fromRange, toRange and parse are plain local names
// here. The page is a chapter document rendered by Readium.
//
// CFIs going out are the part after the `!` only, wrapped in `epubcfi(...)`. CfiBridge.kt
// adds the spine step, which it works out from this page's own URL.

// Readium appends its own nodes to the end of <body>: one container per decoration group
// and a spacer that pads the last column. They are not part of the book.
const READIUM_NODES = '#readium-virtual-page, [data-group]'

const skipReadiumNodes = node =>
    node.nodeType === Node.ELEMENT_NODE &&
    node.parentNode === document.body &&
    node.matches(READIUM_NODES)
        ? NodeFilter.FILTER_REJECT
        : NodeFilter.FILTER_ACCEPT

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
            ) return range
        }
    }
    return null
}

// Up to `length` characters of book text just before (backwards) or just after a range,
// collected one text node at a time so a long chapter is never serialised. Empty when
// that end of the range sits between elements rather than inside text.
const contextOf = (range, backwards, length = 64) => {
    const container = backwards ? range.startContainer : range.endContainer
    const offset = backwards ? range.startOffset : range.endOffset
    if (container.nodeType !== Node.TEXT_NODE) return ''
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, node =>
        node.parentElement.closest(READIUM_NODES) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT)
    walker.currentNode = container
    let text = backwards ? container.nodeValue.slice(0, offset) : container.nodeValue.slice(offset)
    while (text.length < length) {
        const node = backwards ? walker.previousNode() : walker.nextNode()
        if (!node) break
        text = backwards ? node.nodeValue + text : text + node.nodeValue
    }
    return backwards ? text.slice(-length) : text.slice(0, length)
}

window.emberCfi = {
    // Content CFI of the current text selection, or null when nothing is selected.
    forSelection() {
        const selection = window.getSelection()
        if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null
        return fromRange(selection.getRangeAt(0), skipReadiumNodes)
    },

    // Content CFI of the first text position on screen, or null when none is found.
    forVisibleStart() {
        const range = visibleStartRange()
        return range ? fromRange(range, skipReadiumNodes) : null
    },

    // The text a full CFI points at in this chapter, with context on both sides. The
    // caller checks that the CFI's spine step belongs to this chapter.
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
