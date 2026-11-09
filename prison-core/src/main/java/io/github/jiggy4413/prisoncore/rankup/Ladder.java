package io.github.jiggy4413.prisoncore.rankup;

import java.util.List;

/** All entries of the rank or prestige ladder for one player. */
public final class Ladder {

    public final List<LadderEntry> entries;
    /** The player's current entry, or null (no rank yet / not prestiged yet). */
    public final LadderEntry current;
    /** The entry after current, or null if the player is at the top. */
    public final LadderEntry next;
    /** For prestiges: whether the player is allowed to prestige right now (e.g. at max rank). */
    public final boolean canAdvance;

    public Ladder(List<LadderEntry> entries, LadderEntry current, LadderEntry next, boolean canAdvance) {
        this.entries = entries;
        this.current = current;
        this.next = next;
        this.canAdvance = canAdvance;
    }
}
