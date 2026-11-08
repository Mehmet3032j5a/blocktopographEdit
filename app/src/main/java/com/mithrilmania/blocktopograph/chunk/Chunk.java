package com.mithrilmania.blocktopograph.chunk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mithrilmania.blocktopograph.Log;
import com.mithrilmania.blocktopograph.World;
import com.mithrilmania.blocktopograph.WorldData;
import com.mithrilmania.blocktopograph.block.Block;
import com.mithrilmania.blocktopograph.block.KnownBlockRepr;
import com.mithrilmania.blocktopograph.map.Dimension;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

public abstract class Chunk {

    protected final WeakReference<WorldData> mWorldData;
    protected final Version mVersion;
    public final int mChunkX;
    public final int mChunkZ;
    public final Dimension mDimension;
    protected NBTChunkData mEntity;
    protected NBTChunkData mTileEntity;
    boolean mIsVoid;
    boolean mIsError;

    Chunk(WorldData worldData, Version version, int chunkX, int chunkZ, Dimension dimension) {
        mWorldData = new WeakReference<>(worldData);
        mVersion = version;
        mChunkX = chunkX;
        mChunkZ = chunkZ;
        mDimension = dimension;
        mIsVoid = false;
        mIsError = false;
        try {
            mEntity = version.createEntityChunkData(this);
            mTileEntity = version.createBlockEntityChunkData(this);
        } catch (Version.VersionException e) {
            Log.d(this, e);
        }
    }

    public static Chunk createEmpty(@NonNull WorldData worldData, int chunkX, int chunkZ, Dimension dimension,
                                    Version createOfVersion) {
        Chunk chunk;
        switch (createOfVersion) {
            case V1_2_PLUS:
                try {
                    worldData.writeChunkData(chunkX, chunkZ, ChunkTag.GENERATOR_STAGE, dimension, (byte) 0, false, new byte[]{2, 0, 0, 0});
                    worldData.writeChunkData(chunkX, chunkZ, ChunkTag.VERSION, dimension, (byte) 0, false, new byte[]{0xf});
                    chunk = new BedrockChunk(worldData, createOfVersion, chunkX, chunkZ, dimension, true);
                } catch (Exception e) {
                    Log.d(Chunk.class, e);
                    chunk = new VoidChunk(worldData, createOfVersion, chunkX, chunkZ, dimension);
                }
                break;
            default:
                chunk = new VoidChunk(worldData, createOfVersion, chunkX, chunkZ, dimension);
        }
        return chunk;
    }

    public static Chunk create(@NonNull WorldData worldData, int chunkX, int chunkZ, Dimension dimension,
                               boolean createIfMissing, Version createOfVersion) {
        Version version;
        try {
            byte[] data = worldData.getChunkData(chunkX, chunkZ, ChunkTag.VER, dimension, (byte) 0, false);
            if (data == null){
                data = worldData.getChunkData(chunkX, chunkZ, ChunkTag.VERSION, dimension, (byte) 0, false);
            }
            if (data == null && createIfMissing)
                return createEmpty(worldData, chunkX, chunkZ, dimension, createOfVersion);
            version = Version.getVersion(data);
        } catch (WorldData.WorldDBLoadException | WorldData.WorldDBException e) {
            Log.d(Chunk.class, e);
            version = Version.ERROR;
        }
        Chunk chunk;
        switch (version) {
            case ERROR:
            case OLD_LIMITED:
                chunk = new VoidChunk(worldData, version, chunkX, chunkZ, dimension);
                chunk.mIsError = true;
                break;
            case v0_9:
                chunk = new PocketChunk(worldData, version, chunkX, chunkZ, dimension);
                break;
            case V1_0:
            case V1_1:
            case V1_2_PLUS:
            case V1_18_PLUS:
                chunk = new BedrockChunk(worldData, version, chunkX, chunkZ, dimension, false);
                break;
            case NULL:
            default:
                chunk = new VoidChunk(worldData, version, chunkX, chunkZ, dimension);
        }
        return chunk;
    }

    public final WorldData getWorldData() {
        return mWorldData.get();
    }


    public final boolean isVoid() {
        return mIsVoid;
    }


    public final boolean isError() {
        return mIsError;
    }

    public abstract boolean isData3d();

    abstract public boolean supportsBlockLightValues();

    abstract public boolean supportsHeightMap();

    abstract public int getHeightLimit();

    abstract public int getHeightMapValue(int x, int z);


    abstract public int getBiome(int x, int z);

    abstract public void setBiome(int x, int z, int id);

    abstract public int getGrassColor(int x, int z);

    @NonNull
    abstract public Block getBlock(int x, int y, int z);

    @NonNull
    abstract public Block getBlock(int x, int y, int z, int layer);

    abstract public void setBlock(int x, int y, int z, int layer, @NonNull Block block);

    abstract public int getBlockLightValue(int x, int y, int z);

    abstract public int getSkyLightValue(int x, int y, int z);

    abstract public int getHighestBlockYUnderAt(int x, int z, int y);

    abstract public int getCaveYUnderAt(int x, int z, int y);

    abstract public void save() throws WorldData.WorldDBException, IOException;

    @NonNull
    protected Block getAir() {
        return mWorldData.get().mBlockRegistry.createBlock(KnownBlockRepr.B_0_0_AIR);
    }

    public void deleteThis(boolean deleteEntity) throws Exception {
        Log.d(this,"delete: x: "+mChunkX+ " z: "+mChunkZ);
        WorldData worldData = mWorldData.get();
        if (worldData == null) throw new RuntimeException("World data is null.");
//        worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.VERSION, mDimension, (byte) 0, false);
//        worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.DATA_2D, mDimension, (byte) 0, false);
//        worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.DATA_2D_LEGACY, mDimension, (byte) 0, false);
//        worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.PENDING_TICKS, mDimension, (byte) 0, false);
//        worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.GENERATOR_STAGE, mDimension, (byte) 0, false);
//        worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.BIOME_STATE, mDimension, (byte) 0, false);
//        worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.ENTITY, mDimension, (byte) 0, false);
//        worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.BLOCK_ENTITY, mDimension, (byte) 0, false);
//        worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.BLOCK_EXTRA_DATA, mDimension, (byte) 0, false);
//        worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.V0_9_LEGACY_TERRAIN, mDimension, (byte) 0, false);
//        for (byte i = 0; i < 16; i++) {
//            worldData.removeChunkData(mChunkX, mChunkZ, ChunkTag.TERRAIN, mDimension, i, true);
//        }
        byte[] dimention = new byte[]{
                (byte) (mDimension.id & 0xFF),
                (byte) ((mDimension.id >> 8) & 0xFF),
                (byte) ((mDimension.id >> 16) & 0xFF),
                (byte) ((mDimension.id >> 24) & 0xFF),
        };
        byte[] chunk_X_Z = new byte[8];
        System.arraycopy(WorldData.getReversedBytes(mChunkX),0,chunk_X_Z,0,4);
        System.arraycopy(WorldData.getReversedBytes(mChunkZ),0,chunk_X_Z,4,4);
//                {
//                (byte) (mChunkX & 0xFF),
//                (byte) ((mChunkX >> 8) & 0xFF),
//                (byte) ((mChunkX >> 16) & 0xFF),
//                (byte) ((mChunkX >> 24) & 0xFF),
//                (byte) (mChunkZ & 0xFF),
//                (byte) ((mChunkZ >> 8) & 0xFF),
//                (byte) ((mChunkZ >> 16) & 0xFF),
//                (byte) ((mChunkZ >> 24) & 0xFF)
//        };
        List<byte[]> seemsKeys = worldData.findKeysWithPrefix(chunk_X_Z);
        for(byte[] key : seemsKeys){
            if(key.length == 9 || key.length == 10){
                if(mDimension == Dimension.OVERWORLD){
                    worldData.deleteKey(key);
                }
            }else if(key.length == 13 || key.length == 14){
                // Dimension id (nether, end or custom) is stored little endian at 8..11.
                if(mDimension != Dimension.OVERWORLD){
                    int keyDimId = (key[8] & 0xFF)
                            | ((key[9] & 0xFF) << 8)
                            | ((key[10] & 0xFF) << 16)
                            | ((key[11] & 0xFF) << 24);
                    if(keyDimId == mDimension.id){
                        worldData.deleteKey(key);
                    }
                }
            }
        }
        if(deleteEntity){
            byte[] digpString = "digp".getBytes();

            if(mDimension == Dimension.OVERWORLD){
                byte[] digpByte = new byte[12];
                System.arraycopy(digpString,0,digpByte,0,digpString.length);
                System.arraycopy(chunk_X_Z,0,digpByte,digpString.length,chunk_X_Z.length);
                worldData.deleteEntityOfChunk(digpByte);
            }else{
                byte[] digpByte = new byte[16];
                System.arraycopy(digpString,0,digpByte,0,digpString.length);
                System.arraycopy(chunk_X_Z,0,digpByte,digpString.length,chunk_X_Z.length);
                System.arraycopy(dimention,0,digpByte,digpString.length + chunk_X_Z.length,dimention.length);
                worldData.deleteEntityOfChunk(digpByte);
            }
        }
        // Prevent saving.
        mIsError = true;
    }

    
    public final NBTChunkData getEntity() {
        return mEntity;
    }


    public final NBTChunkData getBlockEntity() {
        return mTileEntity;
    }

    abstract public int get3dBiome(int x, int y, int z);
}
