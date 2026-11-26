package sh.okx.rankup.ranks.requirements;

import org.bukkit.entity.Player;
import sh.okx.rankup.requirements.Requirement;

/** Compile-only stub of Rankup 3.15.3 (3.11.3 returned a Set here). */
public interface RankRequirements {
    Iterable<Requirement> getRequirements(Player player);
}
