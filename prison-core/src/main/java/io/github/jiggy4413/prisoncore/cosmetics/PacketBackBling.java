package io.github.jiggy4413.prisoncore.cosmetics;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A back bling that only exists on players' screens: a fake item display entity, sent with PacketEvents,
 * riding on the wearer as a client-side passenger. Each player's game moves it together with the wearer in
 * the same frame (no trailing), and the server never has a real passenger on the player, so teleports from
 * other plugins (/spawn, warps, homes) keep working.
 *
 * Only loaded when the packetevents plugin is on the server (Cosmetics falls back to a following entity).
 */
final class PacketBackBling {

    /** Item display entity data indexes (Minecraft 1.21.4). */
    private static final int POS_ROT_INTERPOLATION = 10;
    private static final int TRANSLATION = 11;
    private static final int SCALE = 12;
    private static final int BILLBOARD = 15;
    private static final int VIEW_RANGE = 17;
    private static final int ITEM = 23;
    private static final int DISPLAY_TYPE = 24;
    private static final byte FIXED = 8;

    final int entityId;
    final UUID uuid = UUID.randomUUID();
    final String cosmeticId;
    private final ItemStack item;
    private final float size;
    private final float up;
    private final float back;
    float lastYaw = Float.NaN;

    PacketBackBling(int entityId, String cosmeticId, ItemStack item, float size, float up, float back) {
        this.entityId = entityId;
        this.cosmeticId = cosmeticId;
        this.item = item;
        this.size = size;
        this.up = up;
        this.back = back;
    }

    static boolean available() {
        try {
            return PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Spawns the fake display for one viewer and seats it on the wearer. */
    void show(Player wearer, Player viewer) {
        Location l = wearer.getLocation();
        float yaw = Float.isNaN(lastYaw) ? wearer.getBodyYaw() : lastYaw;
        send(viewer, new WrapperPlayServerSpawnEntity(entityId, Optional.of(uuid), EntityTypes.ITEM_DISPLAY,
                new Vector3d(l.getX(), l.getY(), l.getZ()), 0f, yaw, yaw, 0, Optional.<Vector3d>empty()));
        List<EntityData> data = new ArrayList<EntityData>();
        data.add(new EntityData(POS_ROT_INTERPOLATION, EntityDataTypes.INT, 1));
        // the rider sits at the top of the wearer; move it down to the back and out behind the body.
        // The entity's yaw turns this offset with the body, so it always stays behind the back.
        data.add(new EntityData(TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(0f, up, back)));
        data.add(new EntityData(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(size, size, size)));
        data.add(new EntityData(BILLBOARD, EntityDataTypes.BYTE, (byte) 0));
        data.add(new EntityData(VIEW_RANGE, EntityDataTypes.FLOAT, 0.6f));
        data.add(new EntityData(ITEM, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(item)));
        data.add(new EntityData(DISPLAY_TYPE, EntityDataTypes.BYTE, FIXED));
        send(viewer, new WrapperPlayServerEntityMetadata(entityId, data));
        send(viewer, new WrapperPlayServerSetPassengers(wearer.getEntityId(), new int[] {entityId}));
        send(viewer, new WrapperPlayServerEntityRotation(entityId, yaw, 0f, false));
    }

    void hide(Player viewer) {
        send(viewer, new WrapperPlayServerDestroyEntities(entityId));
    }

    /** Turns the back bling with the wearer's body. */
    void rotate(Player viewer, float yaw) {
        send(viewer, new WrapperPlayServerEntityRotation(entityId, yaw, 0f, false));
    }

    private static void send(Player viewer, PacketWrapper<?> packet) {
        if (viewer != null && viewer.isOnline()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        }
    }
}
