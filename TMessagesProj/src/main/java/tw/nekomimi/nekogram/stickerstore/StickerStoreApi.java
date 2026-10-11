package tw.nekomimi.nekogram.stickerstore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StickerStoreApi {

    public interface PacksCallback {
        void onSuccess(ArrayList<StickerPackInfo> packs);
        void onError(String message);
    }

    public static final String SORT_POPULAR = "popular";
    public static final String SORT_NEWEST = "newest";

    private static OkHttpClient okHttpClient;

    private static OkHttpClient getClient() {
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
        return okHttpClient;
    }

    public static String getBaseUrl() {
        String url = BuildConfig.STICKER_API_BASE_URL;
        return url == null ? "" : url.trim();
    }

    public static boolean isConfigured() {
        return !getBaseUrl().isEmpty();
    }

    public static void fetchPacks(String category, String sort, int page, int limit, PacksCallback callback) {
        if (!isConfigured()) {
            Utilities.stageQueue.postRunnable(() -> postError(callback, "Sticker store is not configured"));
            return;
        }
        StringBuilder url = new StringBuilder(getBaseUrl());
        if (!url.toString().endsWith("/")) {
            url.append("/");
        }
        url.append("api/v1/stickers?sort=").append(sort == null ? SORT_NEWEST : sort)
                .append("&page=").append(Math.max(1, page))
                .append("&limit=").append(limit <= 0 ? 20 : Math.min(limit, 50));
        if (category != null && !category.isEmpty()) {
            try {
                url.append("&category=").append(URLEncoder.encode(category, "UTF-8"));
            } catch (UnsupportedEncodingException ignored) {
            }
        }

        Request request = new Request.Builder().url(url.toString()).get().build();
        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                FileLog.e("StickerStoreApi: network error", e);
                postError(callback, "Couldn't reach the sticker store");
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        postError(callback, "Sticker store returned an error (" + r.code() + ")");
                        return;
                    }
                    String body = r.body().string();
                    ArrayList<StickerPackInfo> packs = parsePacks(body);
                    postSuccess(callback, packs);
                } catch (Exception e) {
                    FileLog.e("StickerStoreApi: parse error", e);
                    postError(callback, "Couldn't read the sticker store response");
                }
            }
        });
    }

    private static ArrayList<StickerPackInfo> parsePacks(String body) throws Exception {
        ArrayList<StickerPackInfo> result = new ArrayList<>();
        Object root = new JSONTokener(body).nextValue();
        JSONArray array = null;
        if (root instanceof JSONArray) {
            array = (JSONArray) root;
        } else if (root instanceof JSONObject) {
            JSONObject obj = (JSONObject) root;
            for (String key : new String[]{"items", "results", "packs", "data", "stickers"}) {
                if (obj.has(key) && obj.get(key) instanceof JSONArray) {
                    array = obj.getJSONArray(key);
                    break;
                }
            }
        }
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject) {
                    result.add(StickerPackInfo.fromJson((JSONObject) item));
                } else if (item instanceof String) {
                    // Bare short_name string with no metadata - the display name is
                    // still resolved from Telegram itself via StickerSetResolver, same
                    // as the object case, so there's nothing else to set here.
                    StickerPackInfo info = new StickerPackInfo();
                    info.shortName = (String) item;
                    result.add(info);
                }
            }
        }
        return result;
    }

    private static void postSuccess(PacksCallback callback, ArrayList<StickerPackInfo> packs) {
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onSuccess(packs);
            }
        });
    }

    private static void postError(PacksCallback callback, String message) {
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onError(message);
            }
        });
    }
}
