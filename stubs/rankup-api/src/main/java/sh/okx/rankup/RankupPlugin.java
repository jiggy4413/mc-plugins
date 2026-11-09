package sh.okx.rankup;

import org.bukkit.plugin.java.JavaPlugin;
import sh.okx.rankup.prestige.Prestiges;
import sh.okx.rankup.ranks.Rankups;

/** Compile-only stub of Rankup 3.11.3. See stubs/rankup-api/pom.xml. */
public class RankupPlugin extends JavaPlugin {
    public Rankups getRankups() {
        throw new UnsupportedOperationException("stub");
    }

    /** Null when prestiges are disabled in Rankup's config.yml. */
    public Prestiges getPrestiges() {
        throw new UnsupportedOperationException("stub");
    }

    public String formatMoney(double money) {
        throw new UnsupportedOperationException("stub");
    }
}
