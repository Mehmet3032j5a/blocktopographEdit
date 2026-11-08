package com.mithrilmania.blocktopograph.map.picer;

import android.app.Activity;
import android.graphics.Rect;
import android.os.AsyncTask;

import com.litl.leveldb.DB;
import com.litl.leveldb.Iterator;
import com.mithrilmania.blocktopograph.Log;
import com.mithrilmania.blocktopograph.R;
import com.mithrilmania.blocktopograph.World;
import com.mithrilmania.blocktopograph.WorldData;
import com.mithrilmania.blocktopograph.chunk.Version;
import com.mithrilmania.blocktopograph.map.Dimension;
import com.mithrilmania.blocktopograph.util.UiUtil;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

class AnalyzeTask extends AsyncTask<Void, Void, Rect> {

    private final WeakReference<PicerFragment> owner;
    private boolean hasWrongChunks;
    private boolean hasOldChunks;
    private AlertDialog waitDialog;

    AnalyzeTask(PicerFragment owner) {
        this.owner = new WeakReference<>(owner);
    }

    @Override
    protected void onPreExecute() {
        PicerFragment owner = this.owner.get();
        Activity activity;
        if (owner == null || (activity = owner.getActivity()) == null) {
            cancel(true);
            return;
        }
        waitDialog = UiUtil.buildProgressWaitDialog(activity, R.string.picer_progress_analyzing,
                dialogInterface -> {
                    PicerFragment diaFrag = AnalyzeTask.this.owner.get();
                    if (diaFrag != null) diaFrag.dismiss();
                });
        waitDialog.show();
    }

    @Override
    @Nullable
    protected Rect doInBackground(Void... voids) {

        PicerFragment owner = this.owner.get();
        if (owner == null) return null;

        // Get db.
        DB db = null;
        getDb:
        {
            World world = owner.mWorld;
            if (world == null) break getDb;
            WorldData worldData = world.getWorldData();
            try {
                worldData.openDB();
            } catch (Exception e) {
                break getDb;
            }
            db = worldData.db;
        }
        if (db == null) return null;

        Dimension dimension = owner.mDimension;
        // Overworld keys have no dimension id, every other dimension (incl. custom ones) does.
        int verKeyLenOfDim = dimension == Dimension.OVERWORLD ? 9 : 13;

        // We used to separate world into areas or clusters, now that
        // we have selection so just measure whole world here.
        // If it's too large we just say select before using this.
        //List<Area> areas = new ArrayList<>(32);
        //Area.maxDist = 10;
        Rect rect = null;

        // Iterate over all items.
        try {
//            db.put(new byte[]{0, 1, 2, 3, 0, 1, 2, 3, 118}, new byte[]{0});
            Iterator iterator = db.iterator();
            boolean cancelled = false;
            int loopCount = 0;
            byte[] digp = "digp".getBytes();
            byte[] key;
            int x;
            int z;
            int dimId;
            loop:
//            for (iterator.seekToFirst(); iterator.isValid(); iterator.next(), loopCount++) {
            for (iterator.seek(digp); iterator.isValid(); iterator.next(), loopCount++) {

                if (isCancelled()) {
                    cancelled = true;
                    break;
                }

                // Is it a key for a chunk of current dim's version record?
                key = iterator.getKey();
                if (key.length < 4
                        || key[0] != digp[0]
                        || key[1] != digp[1]
                        || key[2] != digp[2]
                        || key[3] != digp[3]) {
                    break;
                }
//                if (key.length != 12 && key.length != 16) continue;
//                if (key[0] != 'd' || key[1] != 'i' || key[2] != 'g' || key[3] != 'p') continue;

                x = (key[4] & 0xFF) | ((key[5] & 0xFF) << 8) | ((key[6] & 0xFF) << 16) | ((key[7] & 0xFF) << 24);

                z = (key[8] & 0xFF) | ((key[9] & 0xFF) << 8) | ((key[10] & 0xFF) << 16) | ((key[11] & 0xFF) << 24);
                if (key.length == 16) {
                    dimId = (key[12] & 0xFF) | ((key[13] & 0xFF) << 8) | ((key[14] & 0xFF) << 16) | ((key[15] & 0xFF) << 24);
                    byte[] legacyKey = new byte[13];
                    System.arraycopy(key,4,legacyKey,0,12);
                    legacyKey[12] = 44;
                    byte[] value = db.get(legacyKey);
                    if(value == null) continue ;
                } else {
                    dimId = 0;
                    byte[] legacyKey = new byte[9];
                    System.arraycopy(key,4,legacyKey,0,8);
                    legacyKey[8] = 44;
                    byte[] value = db.get(legacyKey);
                    if(value == null) continue ;
                }

                if (dimId != dimension.id) continue;
//                if (key.length != verKeyLenOfDim) continue;

//                if (key[verKeyLenOfDim - 1] != (byte) 0x76) continue;
////                ByteBuffer byteBuffer = ByteBuffer.wrap(key);
////                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
////                int x = byteBuffer.getInt();
////                int z = byteBuffer.getInt();
//                int x = (key[0] & 0xFF) | ((key[1] & 0xFF) << 8) | ((key[2] & 0xFF) << 16) | ((key[3] & 0xFF) << 24);
//                int z = (key[4] & 0xFF) | ((key[5] & 0xFF) << 8) | ((key[6] & 0xFF) << 16) | ((key[7] & 0xFF) << 24);
//                // Wrong dim.
////                if (verKeyLenOfDim == 13 && dimension.id != byteBuffer.getInt()) continue;
//                if (verKeyLenOfDim == 13) {
//                    int dimId = (key[8] & 0xFF) | ((key[9] & 0xFF) << 8) | ((key[10] & 0xFF) << 16) | ((key[11] & 0xFF) << 24);
//                    if (dimension.id != dimId) continue;
//                }
//                byte[] value = iterator.getValue();
//                Version version = Version.getVersion(value);
//                // Record unsupported stuff and skip.
//                switch (version) {
//                    case ERROR:
//                    case NULL:
//                        hasWrongChunks = true;
//                        continue loop;
//                    case OLD_LIMITED:
//                        hasOldChunks = true;
//                        continue loop;
//                }

                if (rect != null) {
                    if (x < rect.left) rect.left = x;
                    else if (x > rect.right) rect.right = x;
                    if (z < rect.top) rect.top = z;
                    else if (z > rect.bottom) rect.bottom = z;
                    if (rect.right - rect.left > PicerFragment.MAX_LENGTH
                            || rect.bottom - rect.top > PicerFragment.MAX_LENGTH
                            || loopCount % 36 == 0 && (rect.right - rect.left) * (rect.bottom - rect.top) > PicerFragment.MAX_AREA)
                        break;
                } else {
                    rect = new Rect(x, z, x, z);
                }

                // Add the chunk to area list.
//                add:
//                {
//                    for (Area area : areas) {
//                        // Try add to existing areas.
//                        if (area.add(x, z)) break add;
//                    }
//                    // Failed then create new.
//                    areas.add(new Area(x, z));
//                }
//                // As more chunks were read maybe we can merge more of them.
//                if (loopCount > 16) Area.absMergeList(areas);
            }
            /// //////////
        boolean notAll = false;
            if(rect != null) {
                byte[] left = db.get(genLegacyKey(rect.left - 1, 0, dimension.id));
                byte[] right = db.get(genLegacyKey(rect.right + 1, 0, dimension.id));
                byte[] top = db.get(genLegacyKey(0, rect.top - 1, dimension.id));
                byte[] bottom = db.get(genLegacyKey(0, rect.bottom + 1, dimension.id));
                if (left != null || right != null || top != null || bottom != null) notAll = true;
            }
        Log.d(this,"notAll: "+notAll);
        if(rect == null || notAll){
            byte[] value;
            Version version;
            loop1:
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next(), loopCount++) {

                if (isCancelled()) {
                    cancelled = true;
                    break;
                }

                // Is it a key for a chunk of current dim's version record?
                key = iterator.getKey();
                if (key.length != verKeyLenOfDim) continue;
                if (key[verKeyLenOfDim - 1] != (byte) 0x76) continue;
                x = (key[0] & 0xFF) | ((key[1] & 0xFF) << 8) | ((key[2] & 0xFF) << 16) | ((key[3] & 0xFF) << 24);
                z = (key[4] & 0xFF) | ((key[5] & 0xFF) << 8) | ((key[6] & 0xFF) << 16) | ((key[7] & 0xFF) << 24);
                // Wrong dim.
                if (verKeyLenOfDim == 13) {
                    dimId = (key[8] & 0xFF) | ((key[9] & 0xFF) << 8) | ((key[10] & 0xFF) << 16) | ((key[11] & 0xFF) << 24);
                    if (dimension.id != dimId) continue;
                }
                value = iterator.getValue();
                version = Version.getVersion(value);
                // Record unsupported stuff and skip.
                switch (version) {
                    case ERROR:
                    case NULL:
                        hasWrongChunks = true;
                        continue loop1;
                    case OLD_LIMITED:
                        hasOldChunks = true;
                        continue loop1;
                }

                if (rect != null) {
                    if (x < rect.left) rect.left = x;
                    else if (x > rect.right) rect.right = x;
                    if (z < rect.top) rect.top = z;
                    else if (z > rect.bottom) rect.bottom = z;
                    if (rect.right - rect.left > PicerFragment.MAX_LENGTH
                            || rect.bottom - rect.top > PicerFragment.MAX_LENGTH
                            || loopCount % 36 == 0 && (rect.right - rect.left) * (rect.bottom - rect.top) > PicerFragment.MAX_AREA)
                        break;
                } else {
                    rect = new Rect(x, z, x, z);
                }

                // Add the chunk to area list.
//                add:
//                {
//                    for (Area area : areas) {
//                        // Try add to existing areas.
//                        if (area.add(x, z)) break add;
//                    }
//                    // Failed then create new.
//                    areas.add(new Area(x, z));
//                }
//                // As more chunks were read maybe we can merge more of them.
//                if (loopCount > 16) Area.absMergeList(areas);
            }
        }

            iterator.close();
            if (cancelled) return null;
        } catch (Throwable e) {
            Log.d(this, e);
            return null;
        }

        // Final merge. Redundant? Who cares.
//        Area.absMergeList(areas);
//
//        owner = this.owner.get();
//        if (owner == null) return null;
//
//        if (areas.size() == 0) return null;
//        if (areas.size() == 1) return areas.get(0);
//
//        // Then should decide which area to use based on camera position.
//        // For not let's ignore.
//        return areas.get(0);
        if (rect != null) {
            rect.left *= 16;
            rect.top *= 16;
//            rect.right *= 16 + 15;
            rect.right = rect.right * 16 + 15;
            rect.bottom =rect.bottom * 16 + 15;
        }
        return rect;
    }
    private static byte[] genLegacyKey(int chunkX, int chunkZ, int dimId) {
        byte[] legacyKey;

        if(dimId == 0){
            legacyKey = new byte[9];
            legacyKey[0] = (byte) (chunkX & 0xFF);
            legacyKey[1] = (byte) ((chunkX >> 8) & 0xFF);
            legacyKey[2] = (byte) ((chunkX >> 16) & 0xFF);
            legacyKey[3] = (byte) ((chunkX >> 24) & 0xFF);
            legacyKey[4] = (byte) (chunkZ & 0xFF);
            legacyKey[5] = (byte) ((chunkZ >> 8) & 0xFF);
            legacyKey[6] = (byte) ((chunkZ >> 16) & 0xFF);
            legacyKey[7] = (byte) ((chunkZ >> 24) & 0xFF);
            legacyKey[8] = (byte) 118;
        }else{
            legacyKey = new byte[13];
            legacyKey[0] = (byte) (chunkX & 0xFF);
            legacyKey[1] = (byte) ((chunkX >> 8) & 0xFF);
            legacyKey[2] = (byte) ((chunkX >> 16) & 0xFF);
            legacyKey[3] = (byte) ((chunkX >> 24) & 0xFF);
            legacyKey[4] = (byte) (chunkZ & 0xFF);
            legacyKey[5] = (byte) ((chunkZ >> 8) & 0xFF);
            legacyKey[6] = (byte) ((chunkZ >> 16) & 0xFF);
            legacyKey[7] = (byte) ((chunkZ >> 24) & 0xFF);
            legacyKey[8] = (byte) dimId;
            legacyKey[12] = (byte) 118;
        }
        return legacyKey;
    }
    @Override
    protected void onCancelled() {
        PicerFragment owner = this.owner.get();
        if (owner != null) {
            owner.mOngoingTask = null;
            waitDialog.dismiss();
        }
    }

    @Override
    protected void onPostExecute(@Nullable Rect rect) {

        // We finished too late. No longer needed.
        PicerFragment owner = this.owner.get();
        if (owner == null) return;
        owner.mOngoingTask = null;
        waitDialog.dismiss();

        if (rect != null) {
            owner.onAnalyzeDone(rect);

        } else if (hasWrongChunks || hasOldChunks) {
            // Size 0 with exception, dismiss and show exception in dialog.
            @StringRes int strId;
            if (hasWrongChunks) strId = R.string.picer_failed_corrupt;
            else strId = R.string.picer_failed_old;
            owner.showFailureDialogAndDismiss(strId);

            // Otherwise a toast is enough.
        } else owner.showFailureMsgAndDismiss(R.string.picer_failed_nodata);

    }
}