package com.ember.reader.ui.reader.epub

/**
 * The script [ChapterPullController] runs in a chapter, in scroll mode, to learn that the
 * reader has swiped on past its end or back past its start.
 */
internal object ChapterPullScript {

    /** What the reader asked for by swiping past an edge of the chapter. */
    enum class Pull { NEXT, PREVIOUS }

    /** How far the finger has to travel, in CSS pixels, for a swipe past an edge to count. */
    const val MIN_DISTANCE_PX = 60

    /**
     * Sets the chapter up to notice such a swipe, if it is not already, and hands over the
     * one made since it was last asked; answered by [parsePull].
     *
     * A swipe counts when the finger went down with the chapter already at that edge, moved
     * [MIN_DISTANCE_PX] or more the way that would scroll further, and more up or down than
     * sideways. The swipe that brings the page to its end therefore does not count; the next
     * one does. A chapter shorter than the screen is at both edges at once, and the way the
     * finger went decides.
     */
    const val TAKE = """
        (function() {
            var s = window.__emberChapterPull;
            if (!s) {
                s = window.__emberChapterPull = { pull: '', x: 0, y: 0, atTop: false, atEnd: false, down: false };
                var page = function() { return document.scrollingElement || document.documentElement; };
                document.addEventListener('touchstart', function(e) {
                    if (e.touches.length !== 1) { s.down = false; return; }
                    s.down = true;
                    s.x = e.touches[0].clientX;
                    s.y = e.touches[0].clientY;
                    s.atTop = window.scrollY <= 2;
                    s.atEnd = window.innerHeight + window.scrollY >= page().scrollHeight - 2;
                }, { passive: true });
                document.addEventListener('touchend', function(e) {
                    if (!s.down || !e.changedTouches.length) { return; }
                    s.down = false;
                    var selection = window.getSelection();
                    if (selection && !selection.isCollapsed) { return; }
                    var dx = e.changedTouches[0].clientX - s.x;
                    var dy = e.changedTouches[0].clientY - s.y;
                    if (Math.abs(dy) < $MIN_DISTANCE_PX || Math.abs(dy) < 2 * Math.abs(dx)) { return; }
                    if (dy < 0 && s.atEnd) { s.pull = 'next'; }
                    if (dy > 0 && s.atTop) { s.pull = 'previous'; }
                }, { passive: true });
                document.addEventListener('touchcancel', function() { s.down = false; }, { passive: true });
            }
            var pull = s.pull;
            s.pull = '';
            return pull;
        })();
    """

    /** Reads the answer to [TAKE]. Anything else, or no answer, is no swipe. */
    fun parsePull(result: String?): Pull? = when (result?.trim('"')) {
        "next" -> Pull.NEXT
        "previous" -> Pull.PREVIOUS
        else -> null
    }
}
