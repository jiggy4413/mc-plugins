package sh.okx.rankup.requirements;

import org.bukkit.entity.Player;

/** Compile-only stub of Rankup 3.11.3. */
public abstract class Requirement {
    protected Requirement() {
    }

    /** e.g. "money", "xp-level", "mobkills". */
    public String getName() {
        throw new UnsupportedOperationException("stub");
    }

    /** Sub-requirement, e.g. the mob type for "mobkills zombie 10". May be null. */
    public String getSub() {
        throw new UnsupportedOperationException("stub");
    }

    public abstract boolean check(Player player);

    public double getRemaining(Player player) {
        throw new UnsupportedOperationException("stub");
    }

    public double getTotal(Player player) {
        throw new UnsupportedOperationException("stub");
    }
}
