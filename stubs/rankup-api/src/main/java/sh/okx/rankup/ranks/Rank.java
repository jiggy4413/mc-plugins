package sh.okx.rankup.ranks;

import org.bukkit.entity.Player;
import sh.okx.rankup.ranks.requirements.RankRequirements;

/** Compile-only stub of Rankup 3.11.3. */
public class Rank {
    protected Rank() {
    }

    /** The group name, e.g. "A". */
    public String getRank() {
        throw new UnsupportedOperationException("stub");
    }

    public String getNext() {
        throw new UnsupportedOperationException("stub");
    }

    /** The "display-name" from rankups.yml, may be null. */
    public String getDisplayName() {
        throw new UnsupportedOperationException("stub");
    }

    /** Requirements to go from this rank to the next one. */
    public RankRequirements getRequirements() {
        throw new UnsupportedOperationException("stub");
    }

    public boolean isIn(Player player) {
        throw new UnsupportedOperationException("stub");
    }
}
