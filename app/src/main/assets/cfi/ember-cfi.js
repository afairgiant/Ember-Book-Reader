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
            ) return toLineStart(range)
        }
    }
    return null
}

// caretRangeFromPoint snaps to the nearest gap between characters, which is usually a
// character or more into the line. Walk back to the first character of that line that is
// on screen: earlier text in the node is on the line above or on the previous page.
const toLineStart = range => {
    const node = range.startContainer
    const probe = document.createRange()
    const rectOf = index => {
        probe.setStart(node, index)
        probe.setEnd(node, index + 1)
        return probe.getBoundingClientRect()
    }
    let offset = range.startOffset
    const line = rectOf(Math.min(offset, node.length - 1))
    while (offset > 0) {
        const previous = rectOf(offset - 1)
        const sameLine = Math.abs(previous.top - line.top) < line.height / 2
        if (previous.width === 0 || previous.left < 0 || !sameLine) break
        offset--
    }
    range.setStart(node, offset)
    range.collapse(true)
    return range
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

// The chapter's text with all whitespace removed, and where each remaining character
// lives. A selection's text and the DOM's text nodes disagree about whitespace (a line
// break between two paragraphs is in one and not the other), so passages are matched
// without it.
const WHITESPACE = /\s/
const squeeze = text => (text ?? '').replace(/\s+/g, '')
const chapterText = () => {
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, node =>
        node.parentElement.closest(READIUM_NODES + ', script, style')
            ? NodeFilter.FILTER_REJECT
            : NodeFilter.FILTER_ACCEPT)
    const characters = []
    const nodes = []
    const offsets = []
    for (let node; (node = walker.nextNode());) {
        const value = node.nodeValue
        for (let offset = 0; offset < value.length; offset++) {
            if (WHITESPACE.test(value[offset])) continue
            characters.push(value[offset])
            nodes.push(node)
            offsets.push(offset)
        }
    }
    return { text: characters.join(''), nodes, offsets }
}

const commonSuffix = (a, b) => {
    let length = 0
    while (length < a.length && length < b.length &&
        a[a.length - 1 - length] === b[b.length - 1 - length]) length++
    return length
}
const commonPrefix = (a, b) => {
    let length = 0
    while (length < a.length && length < b.length && a[length] === b[length]) length++
    return length
}

// Finds a passage by its text. When it occurs more than once, the occurrence whose
// surroundings best match `before` and `after` wins, and the first one on a tie.
const findQuote = (chapter, quote) => {
    const exact = squeeze(quote.exact)
    if (!exact) return null
    const before = squeeze(quote.before)
    const after = squeeze(quote.after)
    let best = -1
    let bestScore = -1
    let matches = 0
    for (let at = chapter.text.indexOf(exact); at >= 0; at = chapter.text.indexOf(exact, at + 1)) {
        matches++
        const end = at + exact.length
        const score =
            commonSuffix(chapter.text.slice(Math.max(0, at - before.length), at), before) +
            commonPrefix(chapter.text.slice(end, end + after.length), after)
        if (score > bestScore) {
            best = at
            bestScore = score
        }
    }
    if (best < 0) return null
    const last = best + exact.length - 1
    const range = document.createRange()
    range.setStart(chapter.nodes[best], chapter.offsets[best])
    range.setEnd(chapter.nodes[last], chapter.offsets[last] + 1)
    return { range, matches }
}

// How far into the chapter a range starts, from 0 to 1, in the direction the chapter
// scrolls: across for paginated columns, down otherwise. This is the same measure Readium
// uses for a locator's progression. In paginated columns it is the start of the page the
// range is on, as Readium's own locators are: Readium rounds a progression to the nearest
// page, so one from the right-hand half of a page would open the page after it. Null when
// layout gives no answer; a position that cannot be measured must not stop its text from
// being found.
const progressionOf = range => {
    try {
        const visible = range.cloneRange()
        const node = range.startContainer
        if (visible.collapsed && node.nodeType === Node.TEXT_NODE && range.startOffset < node.length) {
            visible.setEnd(node, range.startOffset + 1)
        }
        const rect = visible.getClientRects()[0] ?? visible.getBoundingClientRect()
        const root = document.scrollingElement
        if (!root || (rect.width === 0 && rect.height === 0)) return null
        const progression = root.scrollWidth > root.clientWidth
            ? Math.floor((rect.left + root.scrollLeft) / root.clientWidth) * root.clientWidth / root.scrollWidth
            : (rect.top + root.scrollTop) / root.scrollHeight
        return Number.isFinite(progression) ? Math.min(1, Math.max(0, progression)) : null
    } catch {
        return null
    }
}

// The first book text at or after a point `progression` (0 to 1) of the way through the
// chapter, in the direction it scrolls: the reverse of progressionOf. The answer is the
// start of a line, since every character before it ends short of that point. A point past
// the last text gives the last text. Null when the chapter has no text or no layout.
const rangeAtProgression = progression => {
    const root = document.scrollingElement
    if (!root || !Number.isFinite(progression)) return null
    const paginated = root.scrollWidth > root.clientWidth && root.scrollHeight <= root.clientHeight
    const target = paginated
        ? progression * root.scrollWidth - root.scrollLeft
        : progression * root.scrollHeight - root.scrollTop
    const endOf = rect => (paginated ? rect.right : rect.bottom)

    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, node =>
        node.nodeValue.trim() && !node.parentElement.closest(READIUM_NODES + ', script, style')
            ? NodeFilter.FILTER_ACCEPT
            : NodeFilter.FILTER_REJECT)
    const probe = document.createRange()
    const at = (node, offset) => {
        probe.setStart(node, offset)
        probe.collapse(true)
        return probe
    }
    let last = null
    for (let node; (node = walker.nextNode());) {
        probe.selectNodeContents(node)
        const whole = probe.getBoundingClientRect()
        if (whole.width === 0 && whole.height === 0) continue
        last = node
        if (endOf(whole) <= target) continue
        for (let offset = 0; offset < node.length; offset++) {
            if (WHITESPACE.test(node.nodeValue[offset])) continue
            probe.setStart(node, offset)
            probe.setEnd(node, offset + 1)
            if (endOf(probe.getBoundingClientRect()) > target) return at(node, offset)
        }
    }
    return last && at(last, last.nodeValue.search(/\S/))
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

    // What each full CFI in a JSON array points at in this chapter: its text (empty for a
    // position), the text around it, and how far into the chapter it is. Null for one that
    // does not resolve. The caller checks that each CFI's spine step belongs to this chapter.
    resolve(cfisJson) {
        return JSON.parse(cfisJson).map(cfi => {
            try {
                const parts = parse(cfi)
                ;(parts.parent ?? parts).shift()
                const range = toRange(document, parts, skipReadiumNodes)
                return {
                    exact: range.toString(),
                    before: contextOf(range, true),
                    after: contextOf(range, false),
                    progression: progressionOf(range),
                }
            } catch {
                return null
            }
        })
    },

    // Content CFIs for positions known only by how far into the chapter they are: a JSON
    // array of progressions. Answers in the same order, null where no text was found. How
    // close an answer is to the page the progression came from depends on this page being
    // laid out like that one.
    forProgressions(progressionsJson) {
        return JSON.parse(progressionsJson).map(progression => {
            const range = rangeAtProgression(progression)
            return range && fromRange(range, skipReadiumNodes)
        })
    },

    // Content CFIs for passages known only by their text: a JSON array of
    // {exact, before, after}. Answers in the same order with {cfi, matches}, or null for a
    // passage this chapter does not contain. `matches` is how often `exact` occurs here.
    forQuotes(quotesJson) {
        const chapter = chapterText()
        return JSON.parse(quotesJson).map(quote => {
            const found = findQuote(chapter, quote)
            return found && { cfi: fromRange(found.range, skipReadiumNodes), matches: found.matches }
        })
    },
}
