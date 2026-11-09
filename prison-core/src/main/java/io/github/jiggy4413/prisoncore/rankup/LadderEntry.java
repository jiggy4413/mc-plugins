package io.github.jiggy4413.prisoncore.rankup;

import java.util.List;

/**
 * One rank (or prestige) as the menu shows it. Plain data with no Rankup classes, so the menu
 * code never touches Rankup directly.
 */
public final class LadderEntry {

    public enum State {
        /** Already passed this rank. */
        COMPLETED,
        /** The player's rank right now. */
        CURRENT,
        /** The very next rank: clicking it ranks up. */
        NEXT,
        /** Further ahead than the next rank. */
        LOCKED
    }

    /** One requirement to reach this entry, e.g. money 5000. */
    public static final class Requirement {
        public final String name;
        public final String sub;
        public final boolean progressive;
        public final double total;
        public final double remaining;
        public final boolean met;

        public Requirement(String name, String sub, boolean progressive, double total, double remaining, boolean met) {
            this.name = name;
            this.sub = sub;
            this.progressive = progressive;
            this.total = total;
            this.remaining = remaining;
            this.met = met;
        }

        public boolean isMoney() {
            return "money".equalsIgnoreCase(name) || "moneyh".equalsIgnoreCase(name);
        }

        /** 0..1 */
        public double progress() {
            if (met) {
                return 1;
            }
            if (!progressive || total <= 0) {
                return 0;
            }
            return Math.max(0, Math.min(1, (total - remaining) / total));
        }
    }

    public final String id;
    /** Rankup's display name, or the id when there isn't one. */
    public final String rankupDisplayName;
    /** 1-based position on the ladder. */
    public final int position;
    public final State state;
    /** What it takes to reach this entry from the one before it. Empty for the first rank. */
    public final List<Requirement> requirements;

    public LadderEntry(String id, String rankupDisplayName, int position, State state, List<Requirement> requirements) {
        this.id = id;
        this.rankupDisplayName = rankupDisplayName;
        this.position = position;
        this.state = state;
        this.requirements = requirements;
    }

    /** Average progress over all requirements, 0..1. */
    public double progress() {
        if (state == State.COMPLETED || state == State.CURRENT) {
            return 1;
        }
        if (requirements.isEmpty()) {
            return state == State.NEXT ? 1 : 0;
        }
        double sum = 0;
        for (Requirement r : requirements) {
            sum += r.progress();
        }
        return sum / requirements.size();
    }

    /** The money requirement's total, or -1 if this entry has no money cost. */
    public double moneyCost() {
        for (Requirement r : requirements) {
            if (r.isMoney()) {
                return r.total;
            }
        }
        return -1;
    }
}
