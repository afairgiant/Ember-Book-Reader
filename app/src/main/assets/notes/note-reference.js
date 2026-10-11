// Decides whether the link the reader just tapped is a reference to a note (a footnote or
// an endnote), so the note can be shown in place instead of leaving the page.
//
// FootnoteScript.kt wraps this file in parentheses and calls it with the link's target:
// the chapter path it points at, and the fragment. The page is a chapter document rendered
// by Readium. The signs it looks for are the ones foliate-js's footnote handler uses.
function (targetPath, targetFragment) {
    const OPS = 'http://www.idpf.org/2007/ops'
    const REFERENCE = /\b(noteref|biblioref|glossref|doc-noteref|doc-biblioref|doc-glossref)\b/
    const NOTE = /\b(footnote|endnote|rearnote|note|footnotes|endnotes|doc-footnote|doc-endnote|doc-endnotes)\b/
    const BLOCK = 'p, li, div, td, dd, blockquote, aside, section, h1, h2, h3, h4, h5, h6, body'
    // "1", "[12]", "(3)", "*", a dagger: what a note marker looks like when it is not raised.
    const MARKER = /^[\[(]?(\d{1,4}|[*†‡§¶]{1,3})[\])]?$/

    const decoded = text => {
        try { return decodeURIComponent(text) } catch { return text }
    }
    const typesOf = element =>
        (element.getAttributeNS(OPS, 'type') ?? element.getAttribute('epub:type') ?? '') +
        ' ' + (element.getAttribute('role') ?? '')

    const isRaised = element => {
        if (!element) return false
        if (element.closest('sup')) return true
        const align = getComputedStyle(element).verticalAlign
        return align === 'super' || align === 'top' || align === 'text-top' || parseFloat(align) > 0
    }

    // A note's own number usually links back to the text and looks just like a reference.
    // It opens its paragraph; a reference comes after the words it annotates.
    const followsText = anchor => {
        const block = anchor.closest(BLOCK)
        if (!block) return false
        const range = document.createRange()
        range.setStart(block, 0)
        range.setEndBefore(anchor)
        return range.toString().trim().length > 0
    }

    const isReference = anchor => {
        if (REFERENCE.test(typesOf(anchor))) return true
        for (let element = anchor.parentElement; element; element = element.parentElement) {
            if (NOTE.test(typesOf(element))) return false
        }
        const text = anchor.textContent.trim()
        if (!text || text.length > 6) return false
        const only = anchor.children.length === 1 ? anchor.firstElementChild : null
        const raised = isRaised(anchor) || (only && only.textContent.trim() === text && isRaised(only))
        return (raised || MARKER.test(text)) && followsText(anchor)
    }

    const path = decoded(targetPath)
    return [...document.querySelectorAll('a[href]')].some(anchor =>
        decoded(anchor.hash.slice(1)) === targetFragment &&
        decoded(anchor.pathname).endsWith('/' + path) &&
        isReference(anchor))
}
