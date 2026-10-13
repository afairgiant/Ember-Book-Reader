package com.ember.reader.ui.reader.epub

import java.util.Locale

/** The scripts [AutoScrollController] runs in a chapter to move it and to ask how it is doing. */
internal object AutoScrollScript {

    /** How the chapter on screen answers [STATE]. */
    enum class State { RUNNING, AT_END, GONE }

    /**
     * Starts the chapter moving at [speed] CSS pixels a second, or changes the speed if it
     * already is. The page is moved once a frame to where it should be by then, so slow
     * speeds stay smooth. A finger on the page holds it; it carries on from wherever the
     * finger leaves it.
     */
    fun start(speed: Float): String = """
        (function() {
            var s = window.__emberAutoScroll;
            if (!s) {
                s = window.__emberAutoScroll = { speed: 0, frame: 0, last: 0, target: 0, touching: false };
                document.addEventListener('touchstart', function() { s.touching = true; }, { passive: true });
                var release = function() { s.touching = false; s.last = 0; };
                document.addEventListener('touchend', release, { passive: true });
                document.addEventListener('touchcancel', release, { passive: true });
                s.step = function(now) {
                    if (!s.speed) { s.frame = 0; return; }
                    if (s.touching || !s.last) {
                        s.target = window.scrollY;
                    } else {
                        s.target += s.speed * Math.min(now - s.last, 100) / 1000;
                        window.scrollTo(window.scrollX, s.target);
                    }
                    s.last = now;
                    s.frame = requestAnimationFrame(s.step);
                };
                s.atEnd = function() {
                    var page = document.scrollingElement || document.documentElement;
                    return window.innerHeight + window.scrollY >= page.scrollHeight - 2;
                };
            }
            s.speed = ${String.format(Locale.ROOT, "%.2f", speed)};
            s.last = 0;
            if (!s.frame) { s.frame = requestAnimationFrame(s.step); }
            return 'running';
        })();
    """.trimIndent()

    /** Stops the chapter where it is. */
    const val STOP = "(function() { var s = window.__emberAutoScroll; if (s) { s.speed = 0; } return 'stopped'; })();"

    /** Asks the chapter whether it is still moving; answered by [parseState]. */
    const val STATE = "(function() { var s = window.__emberAutoScroll; " +
        "if (!s) { return 'gone'; } return s.atEnd() ? 'end' : 'running'; })();"

    /** Reads the answer to [STATE]. No answer means the page is not there to ask. */
    fun parseState(result: String?): State = when (result?.trim('"')) {
        "running" -> State.RUNNING
        "end" -> State.AT_END
        else -> State.GONE
    }
}
