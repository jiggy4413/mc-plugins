package sh.okx.rankup.ranks;

import org.bukkit.entity.Player;

/** Compile-only stub of Rankup 3.11.3. */
public abstract class RankList<T extends Rank> {
    protected RankList() {
    }

    public RankTree<T> getTree() {
        throw new UnsupportedOperationException("stub");
    }

    /** The highest element the player is in, or null if they're in none. */
    public RankElement<T> getByPlayer(Player player) {
        throw new UnsupportedOperationException("stub");
    }
}
