package com.mithrilmania.blocktopograph.map;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.mithrilmania.blocktopograph.R;
import com.mithrilmania.blocktopograph.map.renderer.MapType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A world dimension.
 * <p>
 * Used to be an enum, but Bedrock (script API / add-ons) supports custom dimensions
 * whose numeric ids start at {@link #CUSTOM_ID_START} (1000), so the set of dimensions
 * can no longer be known at compile time.
 * <p>
 * Instances are interned per id: there is exactly one {@link Dimension} object for each id,
 * so identity comparisons ({@code ==}) keep working like they did with the enum.
 */
public final class Dimension {

    /**
     * Custom dimensions registered through the script API start at this id.
     */
    public static final int CUSTOM_ID_START = 1000;

    private static final Map<Integer, Dimension> byId = new ConcurrentHashMap<>();
    private static final Map<String, Dimension> byDataName = new ConcurrentHashMap<>();

    public static final Dimension OVERWORLD = register(new Dimension(0, "overworld", "Overworld", 16, 16, 128, 1, MapType.OVERWORLD_SATELLITE, false));
    public static final Dimension NETHER = register(new Dimension(1, "nether", "Nether", 16, 16, 128, 1, MapType.NETHER, false));
    public static final Dimension END = register(new Dimension(2, "end", "End", 16, 16, 128, 1, MapType.END_SATELLITE, false));//mcpe: SOON^TM /jk

    public final int id;
    public final int chunkW, chunkL, chunkH;
    public final int dimensionScale;
    public final String dataName, name;
    public final MapType defaultMapType;
    /**
     * True for every dimension that is not overworld / nether / end.
     */
    public final boolean isCustom;

    private Dimension(int id, String dataName, String name, int chunkW, int chunkL, int chunkH, int dimensionScale, MapType defaultMapType, boolean isCustom) {
        this.id = id;
        this.dataName = dataName;
        this.name = name;
        this.chunkW = chunkW;
        this.chunkL = chunkL;
        this.chunkH = chunkH;
        this.dimensionScale = dimensionScale;
        this.defaultMapType = defaultMapType;
        this.isCustom = isCustom;
    }

    private static Dimension register(Dimension dimension) {
        byId.put(dimension.id, dimension);
        byDataName.put(dimension.dataName, dimension);
        return dimension;
    }

    /**
     * Get (or lazily create) the dimension with the given numeric id.
     * Custom dimensions are assumed to have an overworld-like layout (y from -64).
     *
     * @return null only for negative ids, which are never valid.
     */
    @Nullable
    public static Dimension getDimension(int id) {
        Dimension dimension = byId.get(id);
        if (dimension != null) return dimension;
        if (id < 0) return null;
        synchronized (Dimension.class) {
            dimension = byId.get(id);
            if (dimension == null) {
                dimension = register(new Dimension(id, customDataName(id), "Custom " + id,
                        16, 16, 128, 1, MapType.OVERWORLD_SATELLITE, true));
            }
            return dimension;
        }
    }

    /**
     * Accepts "overworld", "nether", "end", "custom_1000" or just "1000".
     */
    @Nullable
    public static Dimension getDimension(String dataName) {
        if (dataName == null) return null;
        String key = dataName.trim().toLowerCase();
        Dimension dimension = byDataName.get(key);
        if (dimension != null) return dimension;
        String digits = key.startsWith("custom_") ? key.substring("custom_".length()) : key;
        try {
            return getDimension(Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String customDataName(int id) {
        return "custom_" + id;
    }

    /**
     * The three vanilla dimensions.
     */
    public static Dimension[] values() {
        return new Dimension[]{OVERWORLD, NETHER, END};
    }

    /**
     * Vanilla dimensions plus every custom dimension seen so far, ordered by id.
     */
    @NonNull
    public static List<Dimension> knownDimensions() {
        List<Dimension> list = new ArrayList<>(byId.values());
        java.util.Collections.sort(list, (a, b) -> Integer.compare(a.id, b.id));
        return list;
    }

    /**
     * Whether blocks / heightmaps of this dimension use the overworld layout
     * (world bottom at y=-64, 24 sub-chunks). True for custom dimensions too.
     */
    public boolean isOverworldLike() {
        return this == OVERWORLD || isCustom;
    }

    @StringRes
    public int getName() {
        if (this == OVERWORLD) return R.string.overworld;
        if (this == NETHER) return R.string.nether;
        if (this == END) return R.string.the_end;
        return R.string.custom_dimension;
    }

    /**
     * Localized name for vanilla dimensions, "Custom <id>" for custom ones.
     */
    public String getDisplayName(android.content.Context context) {
        return isCustom ? name : context.getString(getName());
    }

    @Override
    public String toString() {
        return name;
    }
}
