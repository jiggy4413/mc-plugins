package io.github.jiggy4413.prisoncore.mines;

import io.github.jiggy4413.prisoncore.util.Text;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Keeps each mine's mined count up to date as players break and place blocks. */
public final class MineListener implements Listener {

    private final MineManager manager;

    public MineListener(MineManager manager) {
        this.manager = manager;
    }

    /** No mining while a mine is refilling: those blocks are about to be replaced anyway. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreakDuringReset(BlockBreakEvent event) {
        Mine m = manager.mineAt(event.getBlock());
        if (m != null && m.isResetting()) {
            event.setCancelled(true);
            String msg = manager.text().getString("messages.resetting-no-break", "");
            if (msg != null && !msg.isEmpty()) {
                event.getPlayer().sendMessage(Text.color(event.getPlayer(), msg.replace("{mine}", m.display())));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (b.getType().isAir()) {
            return;
        }
        Mine m = manager.mineAt(b);
        if (m != null) {
            manager.blocksChanged(m, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!event.getBlockReplacedState().getType().isAir()) {
            return;
        }
        Mine m = manager.mineAt(event.getBlockPlaced());
        if (m != null) {
            manager.blocksChanged(m, -1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        countExploded(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        countExploded(event.blockList());
    }

    private void countExploded(List<Block> blocks) {
        for (Block b : blocks) {
            if (b.getType().isAir()) {
                continue;
            }
            Mine m = manager.mineAt(b);
            if (m != null) {
                manager.blocksChanged(m, 1);
            }
        }
    }
}
