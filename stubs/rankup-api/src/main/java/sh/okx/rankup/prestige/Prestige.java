package sh.okx.rankup.prestige;

import org.bukkit.entity.Player;
import sh.okx.rankup.ranks.Rank;

/** Compile-only stub of Rankup 3.11.3. */
public class Prestige extends Rank {
    protected Prestige() {
    }

    /** The rank a player must be in to prestige (usually the last rank). */
    public String getFrom() {
        throw new UnsupportedOperationException("stub");
    }

    /** The rank a player is reset to after prestiging. */
    public String getTo() {
        throw new UnsupportedOperationException("stub");
    }

    public boolean isEligible(Player player) {
        throw new UnsupportedOperationException("stub");
    }
}
