package tw.nekomimi.nekogram.stickerstore;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a sticker pack's real Telegram metadata (title, thumbnail, documents) by
 * short_name. The backend catalog only knows short_name/category/rating - everything
 * else comes from Telegram itself, exactly like tapping a t.me/addstickers/<name> link.
 * Results are cached in-memory per short_name so scrolling the grid or reopening the
 * same pack doesn't re-fetch.
 */
public class StickerSetResolver {

    public interface Callback {
        void onResolved(TLRPC.TL_messages_stickerSet stickerSet);
        void onError();
    }

    private static final ConcurrentHashMap<String, TLRPC.TL_messages_stickerSet> cache = new ConcurrentHashMap<>();
    private static final HashMap<String, java.util.ArrayList<Callback>> pending = new HashMap<>();

    public static TLRPC.TL_messages_stickerSet getCached(String shortName) {
        if (shortName == null) return null;
        return cache.get(shortName);
    }

    public static void resolve(int currentAccount, String shortName, Callback callback) {
        if (shortName == null || shortName.isEmpty()) {
            if (callback != null) callback.onError();
            return;
        }
        TLRPC.TL_messages_stickerSet cached = cache.get(shortName);
        if (cached != null) {
            if (callback != null) callback.onResolved(cached);
            return;
        }

        MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
        TLRPC.TL_messages_stickerSet local = mediaDataController.getStickerSetByName(shortName);
        if (local != null) {
            cache.put(shortName, local);
            if (callback != null) callback.onResolved(local);
            return;
        }

        synchronized (pending) {
            java.util.ArrayList<Callback> waiters = pending.get(shortName);
            if (waiters != null) {
                if (callback != null) waiters.add(callback);
                return;
            }
            waiters = new java.util.ArrayList<>();
            if (callback != null) waiters.add(callback);
            pending.put(shortName, waiters);
        }

        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        TLRPC.TL_inputStickerSetShortName inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
        inputStickerSet.short_name = shortName;
        req.stickerset = inputStickerSet;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            java.util.ArrayList<Callback> waiters;
            synchronized (pending) {
                waiters = pending.remove(shortName);
            }
            if (error == null && response instanceof TLRPC.TL_messages_stickerSet) {
                TLRPC.TL_messages_stickerSet result = (TLRPC.TL_messages_stickerSet) response;
                cache.put(shortName, result);
                try {
                    MediaDataController.getInstance(currentAccount).putStickerSet(result, false);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (waiters != null) {
                    for (Callback c : waiters) c.onResolved(result);
                }
            } else {
                if (waiters != null) {
                    for (Callback c : waiters) c.onError();
                }
            }
        }));
    }
}
