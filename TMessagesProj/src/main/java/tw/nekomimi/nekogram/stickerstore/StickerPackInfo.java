package tw.nekomimi.nekogram.stickerstore;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

/**
 * One sticker pack as listed by the backend catalog (short_name, category, rating,
 * download_count, owner_telegram_id). The backend does not store a human display name
 * or an icon - those come from Telegram itself once the pack's real sticker set is
 * resolved by short_name (see StickerSetResolver). Until resolved, {@link #getDisplayTitle()}
 * returns null rather than falling back to the technical short_name, so the UI can show
 * a neutral loading state instead of a technical string.
 */
public class StickerPackInfo {

    public String shortName;
    public String category;
    public double ratingAvg;
    /** How many users rated this pack. */
    public int ratingCount;
    /** Distinct from ratingCount - the backend's own like/favorite counter, when present. */
    public int totalLikes;
    public int downloads;
    public long ownerTelegramId;

    /** Filled in asynchronously by StickerSetResolver once the real Telegram sticker set is fetched. */
    public TLRPC.TL_messages_stickerSet resolvedSet;

    public static StickerPackInfo fromJson(JSONObject obj) {
        StickerPackInfo info = new StickerPackInfo();
        info.shortName = firstString(obj, "short_name", "shortName", "name");
        info.category = firstString(obj, "category", "category_name");
        info.downloads = firstInt(obj, "download_count", "downloads", "downloads_count");
        info.ownerTelegramId = firstLong(obj, "owner_telegram_id", "ownerTelegramId", "owner_id");

        JSONObject ratingObj = obj.optJSONObject("rating");
        if (ratingObj != null) {
            info.ratingAvg = ratingObj.optDouble("avg", 0);
            info.ratingCount = ratingObj.optInt("count", 0);
        } else {
            info.ratingAvg = firstDouble(obj, "avg_rating", "average_rating", "rating");
            info.ratingCount = firstInt(obj, "rating_count", "ratings_count");
        }

        // The backend's own like counter, if it exposes one as its own field. Falls back to
        // the rating count (how many people rated the pack) only when no dedicated likes
        // field is present at all, so a real total_likes value from the API is never
        // overwritten by the unrelated rating count.
        if (hasAny(obj, "total_likes", "likes_count", "likes")) {
            info.totalLikes = firstInt(obj, "total_likes", "likes_count", "likes");
        } else {
            info.totalLikes = info.ratingCount;
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

    private static boolean hasAny(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return true;
            }
        }
        return false;
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

    private static long firstLong(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                // owner_telegram_id may arrive as a JSON string or a number depending on
                // backend serialization - optString + parse handles both safely.
                String raw = obj.optString(key, null);
                if (raw != null && !raw.isEmpty()) {
                    try {
                        return Long.parseLong(raw);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return 0;
    }
}
