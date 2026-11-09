package io.github.jiggy4413.prisoncore.rankup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.okx.rankup.RankupPlugin;
import sh.okx.rankup.prestige.Prestige;
import sh.okx.rankup.prestige.Prestiges;
import sh.okx.rankup.ranks.Rank;
import sh.okx.rankup.ranks.RankElement;
import sh.okx.rankup.ranks.Rankups;
import sh.okx.rankup.requirements.ProgressiveRequirement;
import sh.okx.rankup.requirements.Requirement;

/**
 * The only class that talks to Rankup (3.11.3 by Okx). It reads Rankup's live data every time,
 * so "/pru reload" changes show up without restarting PrisonCore.
 *
 * Only create this when the Rankup plugin is enabled, otherwise Java can't find Rankup's classes.
 */
public final class RankupBridge {

    private final RankupPlugin rankup;

    private RankupBridge(RankupPlugin rankup) {
        this.rankup = rankup;
    }

    public static RankupBridge create(Plugin plugin) {
        if (!(plugin instanceof RankupPlugin)) {
            throw new IllegalStateException("The plugin named Rankup isn't Rankup by Okx: " + plugin.getClass().getName());
        }
        return new RankupBridge((RankupPlugin) plugin);
    }

    /** False if Rankup is disabled or its rankups.yml failed to load. */
    public boolean ranksAvailable() {
        if (!rankup.isEnabled()) {
            return false;
        }
        Rankups rankups = rankup.getRankups();
        return rankups != null && rankups.getTree() != null;
    }

    /** False when "prestige: false" in Rankup's config.yml. */
    public boolean prestigesAvailable() {
        if (!rankup.isEnabled()) {
            return false;
        }
        Prestiges prestiges = rankup.getPrestiges();
        return prestiges != null && prestiges.getTree() != null;
    }

    /** Money formatted the way Rankup does it (1.5K, 2M...). */
    public String formatMoney(double amount) {
        return rankup.formatMoney(amount);
    }

    // ------------------------------------------------------------------ ranks

    public Ladder ranks(Player player) {
        Rankups rankups = rankup.getRankups();
        List<RankElement<Rank>> elements = rankups.getTree().asList();
        RankElement<Rank> currentElement = rankups.getByPlayer(player);
        int current = currentElement == null ? -1 : elements.indexOf(currentElement);

        List<LadderEntry> entries = new ArrayList<LadderEntry>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            RankElement<Rank> element = elements.get(i);
            Rank rank = element.getRank();

            // the final rank's "display name" in Rankup is a generic placeholder ("last rank"),
            // so use its real name instead
            String display = element.hasNext() ? rank.getDisplayName() : null;
            if (display == null || display.isEmpty()) {
                display = rank.getRank();
            }

            LadderEntry.State state = stateFor(i, current);
            List<LadderEntry.Requirement> reqs = i == 0
                    ? Collections.<LadderEntry.Requirement>emptyList()
                    : requirements(elements.get(i - 1).getRank(), player,
                    state == LadderEntry.State.COMPLETED || state == LadderEntry.State.CURRENT);

            entries.add(new LadderEntry(rank.getRank(), display, i + 1, state, reqs));
        }

        LadderEntry cur = current >= 0 ? entries.get(current) : null;
        LadderEntry next = current >= 0 && current + 1 < entries.size() ? entries.get(current + 1) : null;
        return new Ladder(entries, cur, next, next != null);
    }

    // ------------------------------------------------------------------ prestiges

    /**
     * Rankup's prestige list starts with an unnamed "not prestiged yet" step, then each prestige.
     * We skip that first step, so entries are only real prestiges.
     */
    public Ladder prestiges(Player player) {
        Prestiges prestiges = rankup.getPrestiges();
        List<RankElement<Prestige>> elements = prestiges.getTree().asList();
        RankElement<Prestige> currentElement = prestiges.getByPlayer(player);
        // not found = hasn't prestiged yet (and isn't at max rank), same as step 0
        int current = currentElement == null ? 0 : Math.max(0, elements.indexOf(currentElement));

        boolean eligible;
        try {
            eligible = elements.get(current).getRank().isEligible(player);
        } catch (RuntimeException e) {
            eligible = false;
        }

        List<LadderEntry> entries = new ArrayList<LadderEntry>();
        for (int i = 1; i < elements.size(); i++) {
            Prestige prestige = elements.get(i).getRank();
            String id = prestige.getRank();
            if (id == null) {
                continue;
            }
            LadderEntry.State state = stateFor(i, current);
            List<LadderEntry.Requirement> reqs = requirements(elements.get(i - 1).getRank(), player,
                    state == LadderEntry.State.COMPLETED || state == LadderEntry.State.CURRENT);
            entries.add(new LadderEntry(id, id, i, state, reqs));
        }

        LadderEntry cur = null;
        LadderEntry next = null;
        for (LadderEntry e : entries) {
            if (e.state == LadderEntry.State.CURRENT) {
                cur = e;
            } else if (e.state == LadderEntry.State.NEXT) {
                next = e;
            }
        }
        return new Ladder(entries, cur, next, next != null && eligible);
    }

    // ------------------------------------------------------------------ helpers

    private static LadderEntry.State stateFor(int index, int current) {
        if (current < 0) {
            return LadderEntry.State.LOCKED;
        }
        if (index < current) {
            return LadderEntry.State.COMPLETED;
        }
        if (index == current) {
            return LadderEntry.State.CURRENT;
        }
        if (index == current + 1) {
            return LadderEntry.State.NEXT;
        }
        return LadderEntry.State.LOCKED;
    }

    private static List<LadderEntry.Requirement> requirements(Rank from, Player player, boolean alreadyDone) {
        Set<Requirement> set;
        try {
            set = from.getRequirements().getRequirements(player);
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
        if (set == null) {
            return Collections.emptyList();
        }

        List<LadderEntry.Requirement> out = new ArrayList<LadderEntry.Requirement>();
        for (Requirement r : set) {
            try {
                boolean progressive = r instanceof ProgressiveRequirement;
                double total = progressive ? r.getTotal(player) : 1;
                double remaining = alreadyDone ? 0 : r.getRemaining(player);
                boolean met = alreadyDone || r.check(player);
                out.add(new LadderEntry.Requirement(r.getName(), r.getSub(), progressive, total, remaining, met));
            } catch (RuntimeException e) {
                // a broken requirement (e.g. its plugin is missing) shouldn't break the menu
            }
        }
        // money first, then alphabetical, so lore order is stable
        Collections.sort(out, new Comparator<LadderEntry.Requirement>() {
            @Override
            public int compare(LadderEntry.Requirement a, LadderEntry.Requirement b) {
                if (a.isMoney() != b.isMoney()) {
                    return a.isMoney() ? -1 : 1;
                }
                return String.valueOf(a.name).compareToIgnoreCase(String.valueOf(b.name));
            }
        });
        return out;
    }
}
