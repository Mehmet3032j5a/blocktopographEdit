package com.mithrilmania.blocktopograph.map.edit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mithrilmania.blocktopograph.Log;
import com.mithrilmania.blocktopograph.WorldData;
import com.mithrilmania.blocktopograph.chunk.Chunk;
import com.mithrilmania.blocktopograph.chunk.ChunkTag;
import com.mithrilmania.blocktopograph.chunk.NBTChunkData;
import com.mithrilmania.blocktopograph.map.Dimension;
import com.mithrilmania.blocktopograph.map.Entity;
import com.mithrilmania.blocktopograph.nbt.convert.DataConverter;
import com.mithrilmania.blocktopograph.nbt.tags.CompoundTag;
import com.mithrilmania.blocktopograph.nbt.tags.IntTag;
import com.mithrilmania.blocktopograph.nbt.tags.ListTag;
import com.mithrilmania.blocktopograph.nbt.tags.StringTag;
import com.mithrilmania.blocktopograph.nbt.tags.Tag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deletes every entity whose type identifier (e.g. "minecraft:zombie") is in the given set,
 * restricted to the selected area. Meant to get rid of entities causing lag.
 * Players are never removed.
 */
public class ClearEntitiesEdit implements EditTarget.ChunkBasedEdit {

    private static final int MAX_EXCEPTION = 5;
    private static final String PLAYER_ID = "minecraft:player";

    private final Set<String> identifiers;
    private int exceptionCount = 0;
    private int removedCount = 0;

    public ClearEntitiesEdit(@NonNull Set<String> identifiers) {
        this.identifiers = identifiers;
    }

    public int getRemovedCount() {
        return removedCount;
    }

    /**
     * Splits user input (separated by comma, space or newline) into normalized identifiers.
     * "zombie", "Zombie" and "minecraft:zombie" all become "minecraft:zombie".
     */
    @NonNull
    public static Set<String> parseIdentifiers(@Nullable String input) {
        Set<String> result = new HashSet<>();
        if (input == null) return result;
        for (String part : input.split("[,;\\s]+")) {
            part = part.trim().toLowerCase(Locale.ROOT);
            if (part.isEmpty()) continue;
            if (part.indexOf(':') < 0) part = "minecraft:" + part;
            result.add(part);
        }
        return result;
    }

    @Override
    public int edit(Chunk chunk, int fromX, int toX, int fromY, int toY, int fromZ, int toZ) {
        if (identifiers.isEmpty()) return 0;
        try {
            NBTChunkData entityData = chunk.getEntity();
            if (entityData == null) return 0;

            // Selection bounds in absolute world coordinates.
            int minX = chunk.mChunkX * 16 + fromX;
            int maxX = chunk.mChunkX * 16 + toX;
            int minZ = chunk.mChunkZ * 16 + fromZ;
            int maxZ = chunk.mChunkZ * 16 + toZ;
            boolean wholeChunk = fromX == 0 && toX == 15 && fromZ == 0 && toZ == 15;

            if (entityData.dataType == ChunkTag.NEWENTITY) {
                removedCount += clearNewFormat(chunk, minX, maxX, minZ, maxZ, wholeChunk);
            } else {
                removedCount += clearLegacyFormat(chunk, entityData, minX, maxX, minZ, maxZ, wholeChunk);
            }
        } catch (Exception e) {
            if (exceptionCount < MAX_EXCEPTION) {
                Log.d(this, e);
                exceptionCount++;
            }
            return -1;
        }
        return 0;
    }

    /**
     * 1.18.30+ format: "digp" key lists actor ids, each actor is stored under "actorprefix"+id.
     * Only the matching actors are deleted, the others are left untouched byte-for-byte.
     */
    private int clearNewFormat(Chunk chunk, int minX, int maxX, int minZ, int maxZ, boolean wholeChunk) throws Exception {
        WorldData worldData = chunk.getWorldData();
        worldData.openDB();

        byte[] digpKey = makeDigpKey(chunk);
        byte[] digpValue = worldData.db.get(digpKey);
        if (digpValue == null || digpValue.length < 8) return 0;

        List<byte[]> actorKeys = NBTChunkData.parseDigpValue(digpValue);
        ByteBuffer keptIds = ByteBuffer.allocate(actorKeys.size() * 8);
        int removed = 0;
        int kept = 0;

        for (byte[] actorKey : actorKeys) {
            boolean remove = false;
            byte[] actorData = worldData.db.get(actorKey);
            if (actorData != null) {
                try {
                    for (Tag tag : DataConverter.read(actorData)) {
                        if (shouldRemove(tag, minX, maxX, minZ, maxZ, wholeChunk)) {
                            remove = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Unreadable actor, leave it alone.
                    remove = false;
                }
            }
            if (remove) {
                worldData.db.delete(actorKey);
                removed++;
            } else {
                // actorKey = "actorprefix"(11 bytes) + 8 bytes id; digp stores the raw ids.
                keptIds.put(actorKey, 11, 8);
                kept++;
            }
        }

        if (removed > 0) {
            if (kept == 0) {
                worldData.db.delete(digpKey);
            } else {
                byte[] newValue = new byte[kept * 8];
                System.arraycopy(keptIds.array(), 0, newValue, 0, newValue.length);
                worldData.db.put(digpKey, newValue);
            }
        }
        return removed;
    }

    /**
     * Old format: all entities of a chunk in one NBT list stored under the chunk's ENTITY key.
     */
    private int clearLegacyFormat(Chunk chunk, NBTChunkData entityData,
                                  int minX, int maxX, int minZ, int maxZ, boolean wholeChunk) throws Exception {
        entityData.load();
        if (entityData.tags == null || entityData.tags.isEmpty()) return 0;

        List<Tag> kept = new ArrayList<>();
        int removed = 0;
        for (Tag tag : entityData.tags) {
            if (shouldRemove(tag, minX, maxX, minZ, maxZ, wholeChunk)) removed++;
            else kept.add(tag);
        }
        if (removed == 0) return 0;

        WorldData worldData = chunk.getWorldData();
        if (kept.isEmpty()) {
            worldData.removeChunkData(chunk.mChunkX, chunk.mChunkZ, ChunkTag.ENTITY,
                    chunk.mDimension, (byte) 0, false);
        } else {
            entityData.tags = kept;
            entityData.write();
        }
        return removed;
    }

    private boolean shouldRemove(Tag tag, int minX, int maxX, int minZ, int maxZ, boolean wholeChunk) {
        if (!(tag instanceof CompoundTag)) return false;
        CompoundTag compound = (CompoundTag) tag;

        String identifier = readIdentifier(compound);
        if (identifier == null || PLAYER_ID.equals(identifier)) return false;
        if (!identifiers.contains(identifier.toLowerCase(Locale.ROOT))) return false;

        Tag posTag = compound.getChildTagByKey("Pos");
        if (posTag instanceof ListTag) {
            List<Tag> pos = ((ListTag) posTag).getValue();
            if (pos != null && pos.size() >= 3
                    && pos.get(0).getValue() instanceof Number
                    && pos.get(2).getValue() instanceof Number) {
                int x = (int) Math.floor(((Number) pos.get(0).getValue()).doubleValue());
                int z = (int) Math.floor(((Number) pos.get(2).getValue()).doubleValue());
                return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
            }
        }
        // No usable position: only remove when the whole chunk is selected.
        return wholeChunk;
    }

    @Nullable
    private static String readIdentifier(CompoundTag compound) {
        Tag idenTag = compound.getChildTagByKey("identifier");
        if (idenTag instanceof StringTag && ((StringTag) idenTag).getValue() != null) {
            return ((StringTag) idenTag).getValue();
        }
        // Very old entities only have a numeric id.
        Tag idTag = compound.getChildTagByKey("id");
        if (idTag instanceof IntTag && ((IntTag) idTag).getValue() != null) {
            Entity e = Entity.getEntity(((IntTag) idTag).getValue().byteValue());
            if (e != Entity.UNKNOWN) return e.identifier;
        }
        return null;
    }

    private static byte[] makeDigpKey(Chunk chunk) {
        boolean overworld = chunk.mDimension == Dimension.OVERWORLD;
        ByteBuffer buf = ByteBuffer.allocate(overworld ? 12 : 16).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("digp".getBytes());
        buf.putInt(chunk.mChunkX);
        buf.putInt(chunk.mChunkZ);
        if (!overworld) buf.putInt(chunk.mDimension.id);
        return buf.array();
    }
}
