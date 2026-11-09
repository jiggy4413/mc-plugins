package sh.okx.rankup.requirements;

import org.bukkit.entity.Player;

/** Compile-only stub of Rankup 3.11.3. Requirements with an amount (money, xp, kills...). */
public abstract class ProgressiveRequirement extends Requirement {
    protected ProgressiveRequirement() {
    }

    public abstract double getProgress(Player player);
}
