package com.mithrilmania.blocktopograph.chunk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mithrilmania.blocktopograph.Log;
import com.mithrilmania.blocktopograph.WorldData;
import com.mithrilmania.blocktopograph.block.Block;
import com.mithrilmania.blocktopograph.block.BlockType;
import com.mithrilmania.blocktopograph.block.KnownBlockRepr;
import com.mithrilmania.blocktopograph.chunk.terrain.TerrainSubChunk;
import com.mithrilmania.blocktopograph.chunk.terrain.V1d2d13TerrainSubChunk;
import com.mithrilmania.blocktopograph.map.Biome;
import com.mithrilmania.blocktopograph.map.Dimension;
import com.mithrilmania.blocktopograph.util.ColorUtil;
import com.mithrilmania.blocktopograph.util.Noise;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class BedrockChunk extends Chunk {

    private static final int POS_HEIGHTMAP = 0;
    private static final int POS_BIOME_DATA = 0x200;
    public static final int DATA2D_LENGTH = 0x300;

    private boolean mHasBlockLight;
    private final boolean[] mDirtyList;
    private final boolean[] mVoidList;
    private final boolean[] mErrorList;
    private boolean mIs2dDirty;
    private final TerrainSubChunk[] mTerrainSubChunks;
    private volatile ByteBuffer data2D;
    private volatile ByteBuffer newData2d;
    private boolean isData3d = true;
    BedrockChunk(WorldData worldData, Version version, int chunkX, int chunkZ, Dimension dimension,
                 boolean createIfMissing) {
        super(worldData, version, chunkX, chunkZ, dimension);
        mVoidList = new boolean[24];
        mErrorList = new boolean[24];
        mDirtyList = new boolean[24];
        mTerrainSubChunks = new TerrainSubChunk[24];
        load2dData(createIfMissing);
        mHasBlockLight = true;
        mIs2dDirty = false;
    }

    private void load2dData(boolean createIfMissing) {
        if (data2D == null) {
            try {
                byte[] rawData = mWorldData.get().getChunkData(
                        mChunkX, mChunkZ, ChunkTag.DATA_3D, mDimension, (byte) 0, false);
                if (rawData == null) {
                    isData3d = false;
                    rawData = mWorldData.get().getChunkData(
                            mChunkX, mChunkZ, ChunkTag.DATA_2D, mDimension, (byte) 0, false);


                }

                if (rawData == null) {
                    if (createIfMissing) {
                        this.data2D = ByteBuffer.allocate(0x300);
                    } else {
                        mIsError = true;
                        mIsVoid = true;
                    }
                    return;
                }

                this.data2D = ByteBuffer.wrap(rawData);

            } catch (Exception e) {
                mIsError = true;
                mIsVoid = true;
            }
        }
    }

    public V1d2d13TerrainSubChunk tempGetSubChunk() {
        return (V1d2d13TerrainSubChunk) getSubChunk(0, false);
    }

    @Nullable
    private TerrainSubChunk getSubChunk(int which, boolean createIfMissing) {
        if (mIsError || mVoidList[which+4]) return null;
        TerrainSubChunk ret = mTerrainSubChunks[which+4];
        if (ret == null) {
            byte[] raw;
            WorldData worldData = mWorldData.get();
            try {
                raw = worldData.getChunkData(mChunkX, mChunkZ,
                        ChunkTag.TERRAIN, mDimension, (byte) which, true);
                if (raw == null && !createIfMissing) {
                    mVoidList[which+4] = true;
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                mErrorList[which+4] = true;
                mVoidList[which+4] = true;
                return null;
            }
            ret = raw == null ?
                    TerrainSubChunk.createEmpty(8, worldData.mBlockRegistry) :
                    TerrainSubChunk.create(raw, worldData.mBlockRegistry);
            if (ret == null || ret.isError()) {
                mVoidList[which+4] = true;
                mErrorList[which+4] = true;
                ret = null;
            } else if (!ret.hasBlockLight()) mHasBlockLight = false;
            mTerrainSubChunks[which+4] = ret;
        }
        return ret;
    }

    private int get2dOffset(int x, int z) {
        return (z << 4) | x;
    }
    public int get3dBiome(int x, int y, int z) {
        ByteBuffer biome3d = data2D;
        int offset = 512;
        int subchunk = (y >> 4) - ((y < 0) && (y & 15) != 0 ? 1 : 0);

        int localY = y & 15;
        int paletteValue = 127;
        for(int i = -4; i <= subchunk; i++){
            if(offset >= data2D.capacity()) return 127;
            int header = biome3d.get(offset) & 0xff;
            offset ++;
            if(header == 0xff){
                continue;
            }else if(header == 0x01){
                if(i == subchunk){
                    return biome3d.get(offset);
                }
                offset += 4;
            }else{
                int bitsPerIndex = (header >> 1) & 0x7f;
//                int hasData = header & 0x01;
                int blocksPerWord = 32 / bitsPerIndex;
                int numWords = (4096 + blocksPerWord -1) / blocksPerWord;
                int indexBytes = numWords * 4;
                int indexStart = offset;

                offset += indexBytes;
                int paletteLenth = biome3d.get(offset);
                offset +=4;
                if(i == subchunk){
                    int localIndex = (x << 8) | (z << 4) | localY;

                    int wordIndex = localIndex / blocksPerWord;
                    int bitOffset = (localIndex % blocksPerWord) * bitsPerIndex;
//                    int word = biome3d.getInt(indexStart + wordIndex * 4);
                    int word = getLittleEndianInt(biome3d, indexStart + wordIndex * 4);
                    int paletteIndex = (word >>> bitOffset) & ((1 << bitsPerIndex) -1);
//                    int paletteIndexValue = biome3d.get(offset+localIndex/4) >>> (localIndex % 4) & 0x01;
                    paletteValue = biome3d.get(offset+(paletteIndex)*4);
                }

                offset += paletteLenth * 4;

            }



        }
        return paletteValue;
    }
    private static int getLittleEndianInt(ByteBuffer buf, int offset) {
        return ((buf.get(offset + 3) & 0xFF) << 24) |
                ((buf.get(offset + 2) & 0xFF) << 16) |
                ((buf.get(offset + 1) & 0xFF) << 8)  |
                (buf.get(offset) & 0xFF);
    }
    @Override
    public boolean isData3d(){
        return isData3d;
    }

    @Override
    public boolean supportsBlockLightValues() {
        return mHasBlockLight;
    }

    @Override
    public boolean supportsHeightMap() {
        return true;
    }

    @Override
    public int getHeightLimit() {
        if(isData3d && mDimension.isOverworldLike()){
            return 320;
        }else{
            return 256;
        }

    }

    @Override
    public int getHeightMapValue(int x, int z) {
        if (mIsVoid) return 0;
        short h = data2D.getShort(POS_HEIGHTMAP + (get2dOffset(x, z) << 1));
        int heightvalue;
        if(isData3d && mDimension.isOverworldLike()){
            heightvalue = (short) (((h & 0xff) << 8) | ((h >> 8) & 0xff)) - 64;
        }else{
            heightvalue =(short) ((h & 0xff) << 8) | ((h >> 8) & 0xff);
        }
        if(heightvalue == -64 && mDimension.isOverworldLike()){
            heightvalue = getHighestBlockYUnderAt(x,z,319)+1;
        }
        return heightvalue;
    }
    private void setHeightMapValue(int x, int z, short height) {
        if (mIsVoid) return;
        data2D.putShort(POS_HEIGHTMAP + (get2dOffset(x, z) << 1), Short.reverseBytes(height));
    }
//    @Override
//    public boolean getIsData3d(){
//        return this.isData3d;
//    }
    @Override
    public int getBiome(int x, int z) {
        if (mIsVoid) return 127;
        if(isData3d){
            int y;
            if(mDimension == Dimension.getDimension(1)) {
                y = 64;
            }else {
                y= getHeightMapValue(x, z);
            }
            int biomeId = get3dBiome(x,y,z);
            if(biomeId == 127){
                biomeId = get3dBiome(x,0,z);
            }
            return biomeId;
        }else{
            return data2D.get(POS_BIOME_DATA + get2dOffset(x, z));
        }

    }

    @Override
    public void setBiome(int x, int z, int id) {
        if (mIsVoid) return;
        if(isData3d){
            if(newData2d == null){
                newData2d = ByteBuffer.allocate(DATA2D_LENGTH);
                for (int idx = 0; idx < POS_BIOME_DATA / 2; idx++) {
                    int offset = POS_HEIGHTMAP + (idx << 1);

                    short height = Short.reverseBytes(data2D.getShort(offset));
                    height = (short) (height - 64);
                    newData2d.putShort(offset, Short.reverseBytes(height));
                }
                int biomeId;
                for(int localX=0; localX<16;localX++){
                    for(int localZ=0; localZ<16;localZ++){
                        biomeId = getBiome(localX,localZ);
                        newData2d.put(POS_BIOME_DATA + get2dOffset(localX, localZ), (byte) biomeId);
                    }
                }
            }
            newData2d.put(POS_BIOME_DATA + get2dOffset(x, z), (byte) id);
        }else{
            data2D.put(POS_BIOME_DATA + get2dOffset(x, z), (byte) id);
        }

        mIs2dDirty = true;
    }

    private int getNoise(int x, int z) {
        // noise values are between -1 and 1
        // 0.0001 is added to the coordinates because integer values result in 0
        double xval = (mChunkX << 4) | x;
        double zval = (mChunkZ << 4) | z;
        double oct1 = Noise.noise(
                (xval / 100.0) % 256 + 0.0001,
                (zval / 100.0) % 256 + 0.0001);
        double oct2 = Noise.noise(
                (xval / 20.0) % 256 + 0.0001,
                (zval / 20.0) % 256 + 0.0001);
        double oct3 = Noise.noise(
                (xval / 3.0) % 256 + 0.0001,
                (zval / 3.0) % 256 + 0.0001);
        return (int) (60 + (40 * oct1) + (14 * oct2) + (6 * oct3));
    }

    @Override
    public int getGrassColor(int x, int z) {
        Biome biome;
        if(isData3d){
            int y = getHeightMapValue(x,z);
            biome = Biome.getBiome(get3dBiome(x,y,z) & 0xff);
        }else {
            biome = Biome.getBiome(getBiome(x, z) & 0xff);
        }
        int noise = getNoise(x, z);
        int r = 30 + (biome.color.red / 5) + noise;
        int g = 110 + (biome.color.green / 5) + noise;
        int b = 30 + (biome.color.blue / 5) + noise;
        return ColorUtil.truncateRgb(r, g, b);
    }

    @NonNull
    @Override
    public Block getBlock(int x, int y, int z) {
        return getBlock(x, y, z, 0);
    }

    @NonNull
    @Override
    public Block getBlock(int x, int y, int z, int layer) {
        if (x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            return getAir();
        TerrainSubChunk subChunk = getSubChunk(y >> 4, false);
        if (subChunk == null)
            return getAir();
        //        int color = block.getColor();

        return subChunk.getBlock(x, y & 0xf, z, layer);
    }

    @Override
    public void setBlock(int x, int y, int z, int layer, @NonNull Block block) {
        if (x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            return;

        int which = y >> 4;
        TerrainSubChunk subChunk = getSubChunk(which, true);

        if (subChunk == null) return;
        subChunk.setBlock(x, y & 0xf, z, layer, block);
        mDirtyList[which+4] = true;
        KnownBlockRepr repr = block.getLegacyBlock();

        // Height increased.
        if (repr != KnownBlockRepr.B_0_0_AIR && getHeightMapValue(x, z) < y) {
            mIs2dDirty = true;
            setHeightMapValue(x, z, (short) (y + 1));
            // Roof removed.
        } else if (repr == KnownBlockRepr.B_0_0_AIR && getHeightMapValue(x, z) == y) {
            mIs2dDirty = true;
            int height = 0;
            for (int h = y - 1; h >= 0; h--) {
                Block blockAtHeight = getBlock(x, h, z);
                KnownBlockRepr reprAtHeight = blockAtHeight.getLegacyBlock();
                if (reprAtHeight != KnownBlockRepr.B_0_0_AIR) {
                    height = h + 1;
                    break;
                }
            }
            setHeightMapValue(x, z, (short) height);
        }
    }

    @Override
    public int getBlockLightValue(int x, int y, int z) {
        if (!mHasBlockLight || x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            return 0;
        TerrainSubChunk subChunk = getSubChunk(y >> 4, false);
        if (subChunk == null) return 0;
        return subChunk.getBlockLightValue(x, y & 0xf, z);
    }

    @Override
    public int getSkyLightValue(int x, int y, int z) {
        if (x >= 16 || y >= 256 || z >= 16 || x < 0 || y < 0 || z < 0 || mIsVoid)
            return 0;
        TerrainSubChunk subChunk = getSubChunk(y >> 4, false);
        if (subChunk == null) return 0;
        return subChunk.getSkyLightValue(x, y & 0xf, z);
    }

    @Override
    public int getHighestBlockYUnderAt(int x, int z, int y) {
        if (x >= 16 || y >= 320 || z >= 16 || x < 0 || y < -64 || z < 0 || mIsVoid)
            return -1;
        TerrainSubChunk subChunk;
        for (int which = y >> 4; which >= -4; which--) {
            subChunk = getSubChunk(which, false);
            if (subChunk == null) continue;
            for (int innerY = (which == (y >> 4)) ? y & 0xf : 15; innerY >= 0; innerY--) {
                Block block = subChunk.getBlock(x, innerY, z, 0);
                KnownBlockRepr repr = block.getLegacyBlock();
                if (repr != KnownBlockRepr.B_0_0_AIR)
                    return (which << 4) | innerY;
            }
        }
        return -1;
    }

    @Override
    public int getCaveYUnderAt(int x, int z, int y) {
        if (x >= 16 || y >= 256 || z >= 16 || x < 0 || y < 0 || z < 0 || mIsVoid)
            return -1;
        TerrainSubChunk subChunk;
        for (int which = y >> 4; which >= 0; which--) {
            subChunk = getSubChunk(which, false);
            if (subChunk == null) continue;
            for (int innerY = (which == (y >> 4)) ? y & 0xf : 15; innerY >= 0; innerY--) {
                Block block = subChunk.getBlock(x, innerY, z, 0);
                if (block.getLegacyBlock() == KnownBlockRepr.B_0_0_AIR)
                    return (which << 4) | innerY;
            }
        }
        return -1;
    }

    @Override
    public void save() throws WorldData.WorldDBException, IOException {

        if (mIsError || mIsVoid) return;

        WorldData worldData = mWorldData.get();
        if (worldData == null)
            throw new RuntimeException("World data is null.");

        // Save biome and hightmap.
        if (mIs2dDirty){
            if(isData3d){
                mWorldData.get().deleteKey(WorldData.getChunkDataKey(mChunkX, mChunkZ, ChunkTag.DATA_3D, mDimension, (byte) 0, false));
                data2D = newData2d;
            }
            worldData.writeChunkData(
                    mChunkX, mChunkZ, ChunkTag.DATA_2D, mDimension, (byte) 0, false, data2D.array());
        }


        // Save subChunks.
        for (int i = 0, mTerrainSubChunksLength = mTerrainSubChunks.length; i < mTerrainSubChunksLength; i++) {
            TerrainSubChunk subChunk = mTerrainSubChunks[i];
            if (subChunk == null || mVoidList[i] || !mDirtyList[i]) continue;
            subChunk.save(worldData, mChunkX, mChunkZ, mDimension, i-4);
        }
    }
}
