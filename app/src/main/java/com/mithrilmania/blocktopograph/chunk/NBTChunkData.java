package com.mithrilmania.blocktopograph.chunk;

import com.mithrilmania.blocktopograph.Log;
import com.mithrilmania.blocktopograph.World;
import com.mithrilmania.blocktopograph.WorldActivity;
import com.mithrilmania.blocktopograph.WorldData;
import com.mithrilmania.blocktopograph.map.Dimension;
import com.mithrilmania.blocktopograph.nbt.convert.DataConverter;
import com.mithrilmania.blocktopograph.nbt.tags.CompoundTag;
import com.mithrilmania.blocktopograph.nbt.tags.IntTag;
import com.mithrilmania.blocktopograph.nbt.tags.LongTag;
import com.mithrilmania.blocktopograph.nbt.tags.StringTag;
import com.mithrilmania.blocktopograph.nbt.tags.Tag;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


public class NBTChunkData extends ChunkData {

    public List<Tag> tags = new ArrayList<>();

    public final ChunkTag dataType;

    public List<byte[]> actorKeys;
    public int entityCount = 1;

    public NBTChunkData(Chunk chunk, ChunkTag dataType) {
        super(chunk);
        this.dataType = dataType;
    }

    public void load() throws WorldData.WorldDBLoadException, WorldData.WorldDBException, IOException {
        Chunk chunk = this.chunk.get();
        WorldData worldData = chunk.getWorldData();
        if (dataType == ChunkTag.NEWENTITY) {
            ByteBuffer buf;
            if(chunk.mDimension == Dimension.OVERWORLD) {
                buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
                buf.put("digp".getBytes());
                buf.putInt(chunk.mChunkX);
                buf.putInt(chunk.mChunkZ);
            }else{
                buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
                buf.put("digp".getBytes());
                buf.putInt(chunk.mChunkX);
                buf.putInt(chunk.mChunkZ);
                buf.putInt(chunk.mDimension.id);
            }
            // The native lookup reads the key from position() to limit(); after the puts above
            // position == limit, i.e. an empty key, so no entity was ever found. Rewind first.
            buf.flip();
            byte[] actorprofix = chunk.getWorldData().getDataWithKey(buf);
            if(actorprofix == null) return;

            actorKeys = parseDigpValue(actorprofix);
            if (actorprofix != null){
                ArrayList<Tag> tags = new ArrayList<>();
                ArrayList<Tag> tag = null;
                for(byte[] actorKey : actorKeys){
                    byte[] actorData = worldData.getDataWithKey(ByteBuffer.wrap(actorKey));
                    if(actorData != null){
                        tag = DataConverter.read(actorData);
                    }
                    
                    if(tag != null){
                        tags.addAll(tag);
                    }
                }
                this.tags = tags;
            }

        } else {
            loadFromByteArray(chunk.getWorldData().getChunkData(chunk.mChunkX, chunk.mChunkZ, dataType, chunk.mDimension, (byte) 0, false));
        }
    }

    public static List<byte[]> parseDigpValue(byte[] value) {
        List<byte[]> actorKeys = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer idBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        while (buf.remaining() >= 8){
            long actorId = buf.getLong();
            idBuf.clear();
            idBuf.putLong(actorId);
            actorKeys.add(makeActorKey(idBuf.array()));
        }
        return actorKeys;
    }

    public static byte[] makeActorKey(byte[] actorId){
        ByteBuffer buf = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("actorprefix".getBytes());
//        System.out.println("actorId.length = " + actorId.length);
        buf.put(actorId);
        return buf.array();
    }

    public void loadFromByteArray(byte[] data) throws IOException {
        if (data != null && data.length > 0) this.tags = DataConverter.read(data);
    }

    public static long makeUniqueId(long entityCount) {
        Long worldStartCount = WorldActivity.WORLD_START_COUNT;
        return entityCount | (worldStartCount << 32);
    }

    public static byte[] makeStorageKey(long uniqueId) {
        Long worldStartCount = WorldActivity.WORLD_START_COUNT;
        worldStartCount--;

        long keyVal = (-worldStartCount << 32) | (uniqueId & 0xFFFFFFFFL);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(keyVal);
        return buffer.array();
    }


    public void write() throws WorldData.WorldDBException, IOException {
        if (this.tags == null) this.tags = new ArrayList<>();

        if(dataType == ChunkTag.NEWENTITY){
            if (actorKeys == null) {
                return;
            }

            Chunk chunk = this.chunk.get();
            WorldData worldData = chunk.getWorldData();
            worldData.openDB();

            List<byte[]> newStorageKeys = new ArrayList<>();
            List<Tag> newTags = new ArrayList<>();
            HashMap<ByteArrayKey, Integer> storageKeyCount = new HashMap<>();
            HashMap<ByteArrayKey, Integer> storageKeyProcessed = new HashMap<>();

            for (Tag tag : this.tags) {
                if (tag instanceof CompoundTag) {
                    CompoundTag compound = (CompoundTag) tag;
                    Tag internalComponents = compound.getChildTagByKey("internalComponents");
                    if (internalComponents instanceof CompoundTag) {
                        CompoundTag internalComp = (CompoundTag) internalComponents;
                        Tag entityStorageKeyComp = internalComp.getChildTagByKey("EntityStorageKeyComponent");
                        if (entityStorageKeyComp instanceof CompoundTag) {
                            CompoundTag storageKeyComp = (CompoundTag) entityStorageKeyComp;
                            Tag storageKeyTag = storageKeyComp.getChildTagByKey("StorageKey");
                            if (storageKeyTag != null) {
                                byte[] storageKey = getStorageKeyBytes(storageKeyTag);
                                if (storageKey != null) {
                                    newStorageKeys.add(storageKey);
                                    newTags.add(tag);

                                    ByteArrayKey key = new ByteArrayKey(storageKey);
                                    storageKeyCount.put(key, storageKeyCount.getOrDefault(key, 0) + 1);
                                }
                            }
                        }
                    }
                }
            }

            List<byte[]> deletedActorKeys = new ArrayList<>();

            for (byte[] actorKey : actorKeys) {
                byte[] actorIdBytes = new byte[8];
                System.arraycopy(actorKey, 11, actorIdBytes, 0, 8);

                boolean found = false;
                for (byte[] storageKey : newStorageKeys) {
                    if (Arrays.equals(actorIdBytes, storageKey)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    deletedActorKeys.add(actorKey);
                }
            }

            List<byte[]> updatedActorKeys = new ArrayList<>();

            for (int i = 0; i < newStorageKeys.size(); i++) {
                byte[] storageKey = newStorageKeys.get(i);
                Tag tag = newTags.get(i);

                ByteArrayKey currentKey = new ByteArrayKey(storageKey);
                int count = storageKeyCount.getOrDefault(currentKey, 0);
                int processed = storageKeyProcessed.getOrDefault(currentKey, 0);

                byte[] existingActorKey = null;
                for (byte[] actorKey : actorKeys) {
                    byte[] actorIdBytes = new byte[8];
                    System.arraycopy(actorKey, 11, actorIdBytes, 0, 8);
                    if (Arrays.equals(actorIdBytes, storageKey)) {
                        existingActorKey = actorKey;
                        break;
                    }
                }

                boolean needNewKey = false;

                if (storageKey.length != 8) {
                    needNewKey = true;
                }
                else if (count > 1 && processed > 0) {
                    needNewKey = true;
                }
                storageKeyProcessed.put(currentKey, processed + 1);

                byte[] finalActorKey;
                long finalUniqueId = -1;

                if (needNewKey) {
                    long uniqueId;
                    byte[] newStorageKey;
                    byte[] newActorKey;

                    do {
                        uniqueId = makeUniqueId(entityCount);
                        newStorageKey = makeStorageKey(uniqueId);
                        newActorKey = makeActorKeyFromStorageKey(newStorageKey);

                        byte[] existingData = worldData.db.get(newActorKey);
                        entityCount++;

                        if (existingData == null) {
                            break;
                        }
                    } while (true);

                    updateTagStorageKey((CompoundTag) tag, newStorageKey);
                    updateTagUniqueId((CompoundTag) tag, uniqueId);

                    finalActorKey = newActorKey;
                    finalUniqueId = uniqueId;
                } else if (existingActorKey != null) {
                    finalActorKey = existingActorKey;
                } else {
                    byte[] testActorKey = makeActorKeyFromStorageKey(storageKey);
                    byte[] existingData = worldData.db.get(testActorKey);

                    if (existingData != null) {
                        long uniqueId;
                        byte[] newStorageKey;
                        byte[] newActorKey;

                        do {
                            uniqueId = makeUniqueId(entityCount);
                            newStorageKey = makeStorageKey(uniqueId);
                            newActorKey = makeActorKeyFromStorageKey(newStorageKey);

                            existingData = worldData.db.get(newActorKey);
                            entityCount++;

                            if (existingData == null) {
                                break;
                            }
                        } while (true);

                        updateTagStorageKey((CompoundTag) tag, newStorageKey);
                        updateTagUniqueId((CompoundTag) tag, uniqueId);
                        finalActorKey = newActorKey;
                        finalUniqueId = uniqueId;
                    } else {
                        finalActorKey = testActorKey;
                    }
                }

                ArrayList<Tag> singleTagList = new ArrayList<>();
                singleTagList.add(tag);
                byte[] nbtData = DataConverter.write(singleTagList);
                worldData.db.put(finalActorKey, nbtData);

                updatedActorKeys.add(finalActorKey);
            }

            for (byte[] deletedKey : deletedActorKeys) {
                worldData.db.delete(deletedKey);
            }

            this.actorKeys = updatedActorKeys;

            byte[] digpValue = buildDigpValue(this.actorKeys);

            ByteBuffer digpBuf;
            if(chunk.mDimension == Dimension.OVERWORLD) {
                digpBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
                digpBuf.put("digp".getBytes());
                digpBuf.putInt(chunk.mChunkX);
                digpBuf.putInt(chunk.mChunkZ);
            } else {
                digpBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
                digpBuf.put("digp".getBytes());
                digpBuf.putInt(chunk.mChunkX);
                digpBuf.putInt(chunk.mChunkZ);
                digpBuf.putInt(chunk.mDimension.id);
            }

            worldData.db.put(digpBuf.array(), digpValue);

        } else {
            byte[] data = DataConverter.write(this.tags);
            Chunk chunk = this.chunk.get();
            chunk.getWorldData().writeChunkData(chunk.mChunkX, chunk.mChunkZ, this.dataType, chunk.mDimension, (byte) 0, false, data);
        }
    }

    private static class ByteArrayKey {
        private final byte[] data;
        private final int hashCode;

        public ByteArrayKey(byte[] data) {
            this.data = data;
            this.hashCode = Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ByteArrayKey other = (ByteArrayKey) obj;
            return Arrays.equals(data, other.data);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private byte[] getStorageKeyBytes(Tag storageKeyTag) {
        if (storageKeyTag instanceof StringTag) {
            StringTag stringTag = (StringTag) storageKeyTag;
            String value = stringTag.getValue();
            if (value != null) {
                try {
                    return value.getBytes("ISO-8859-1");
                } catch (Exception e) {
                    return value.getBytes();
                }
            }
        }
        return null;
    }

    private byte[] makeActorKeyFromStorageKey(byte[] storageKey) {
        ByteBuffer buf = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("actorprefix".getBytes());
        buf.put(storageKey);
        return buf.array();
    }

    private void updateTagStorageKey(CompoundTag compound, byte[] newStorageKey) {
        Tag internalComponents = compound.getChildTagByKey("internalComponents");
        if (internalComponents instanceof CompoundTag) {
            CompoundTag internalComp = (CompoundTag) internalComponents;
            Tag entityStorageKeyComp = internalComp.getChildTagByKey("EntityStorageKeyComponent");
            if (entityStorageKeyComp instanceof CompoundTag) {
                CompoundTag storageKeyComp = (CompoundTag) entityStorageKeyComp;
                try {
                    String storageKeyStr = new String(newStorageKey, "ISO-8859-1");
                    ArrayList<Tag> tags = storageKeyComp.getValue();
                    if (tags != null) {
                        for (int i = 0; i < tags.size(); i++) {
                            Tag tag = tags.get(i);
                            if ("StorageKey".equals(tag.getName())) {
                                tags.set(i, new StringTag("StorageKey", storageKeyStr));
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.d(this, "Failed to update StorageKey: " + e.getMessage());
                }
            }
        }
    }

    private void updateTagUniqueId(CompoundTag compound, long newUniqueId) {
        try {
            ArrayList<Tag> tags = compound.getValue();
            if (tags != null) {
                for (int i = 0; i < tags.size(); i++) {
                    Tag tag = tags.get(i);
                    if ("UniqueID".equals(tag.getName())) {
                        tags.set(i, new LongTag("UniqueID", newUniqueId));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.d(this, "Failed to update UniqueID: " + e.getMessage());
        }
    }

    private byte[] buildDigpValue(List<byte[]> actorKeys) {
        ByteBuffer buf = ByteBuffer.allocate(actorKeys.size() * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (byte[] actorKey : actorKeys) {
            byte[] actorIdBytes = new byte[8];
            System.arraycopy(actorKey, 11, actorIdBytes, 0, 8);
            long actorId = ByteBuffer.wrap(actorIdBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
            buf.putLong(actorId);
        }
        return buf.array();
    }

    @Override
    public void createEmpty() {
        if (this.tags == null) this.tags = new ArrayList<>();
        this.tags.add(new IntTag("Placeholder", 42));
    }
}