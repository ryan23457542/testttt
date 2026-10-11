package tw.nekomimi.nekogram.stickerstore;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

/**
 * One sticker pack as listed by the backend catalog (short_name, category, rating,
 * download_count). The backend does not store a human display name or an icon - those
 * come from Telegram itself once the pack's real sticker set is resolved by short_name
 * (see StickerSetResolver). Until resolved, {@link #getDisplayTitle()} returns null
 * rather than falling back to the technical short_name, so the UI can show a neutral
 * loading state instead of a technical string.
 */
public class StickerPackInfo {

    public String shortName;
    public String category;
    public double ratingAvg;
    public int ratingCount;
    public int downloads;

    /** Filled in asynchronously by StickerSetResolver once the real Telegram sticker set is fetched. */
    public TLRPC.TL_messages_stickerSet resolvedSet;

    public static StickerPackInfo fromJson(JSONObject obj) {
        StickerPackInfo info = new StickerPackInfo();
        info.shortName = firstString(obj, "short_name", "shortName", "name");
        info.category = firstString(obj, "category", "category_name");
        info.downloads = firstInt(obj, "download_count", "downloads", "downloads_count");

        JSONObject ratingObj = obj.optJSONObject("rating");
        if (ratingObj != null) {
            info.ratingAvg = ratingObj.optDouble("avg", 0);
            info.ratingCount = ratingObj.optInt("count", 0);
        } else {
            info.ratingAvg = firstDouble(obj, "avg_rating", "average_rating", "rating");
            info.ratingCount = firstInt(obj, "rating_count", "total_likes", "ratings_count");
        }
        return info;
    }

    /** The real, human-readable pack name from Telegram - null until resolved, never the short_name. */
    public String getDisplayTitle() {
        if (resolvedSet != null && resolvedSet.set != null && resolvedSet.set.title != null && !resolvedSet.set.title.isEmpty()) {
            return resolvedSet.set.title;
        }
        return null;
    }

    public TLRPC.Document getFirstSticker() {
        if (resolvedSet != null && resolvedSet.documents != null && !resolvedSet.documents.isEmpty()) {
            return resolvedSet.documents.get(0);
        }
        return null;
    }

    private static String firstString(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                String value = obj.optString(key, null);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static double firstDouble(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.optDouble(key, 0);
            }
        }
        return 0;
    }

    private static int firstInt(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.optInt(key, 0);
            }
        }
        return 0;
    }
}
