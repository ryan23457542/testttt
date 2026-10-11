package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

/**
 * Our own sticker-pack detail/preview screen, used instead of Telegram's built-in
 * StickersAlert so the Sticker Store keeps its own look: pack name, category, rating,
 * download count, a grid of the pack's real stickers, and an Add/Remove button that
 * installs or removes the set directly.
 */
public class StickerPackPreviewSheet extends BottomSheet {

    private final BaseFragment fragment;
    private final StickerPackInfo pack;

    private final BackupImageView iconView;
    private final TextView titleView;
    private final TextView categoryView;
    private final TextView statsView;
    private final RecyclerListView gridView;
    private final RadialProgressView progressView;
    private final TextView errorView;
    private final TextView actionButton;

    private TLRPC.TL_messages_stickerSet stickerSet;

    public StickerPackPreviewSheet(Context context, BaseFragment fragment, StickerPackInfo pack, int currentAccount) {
        super(context, false);
        this.fragment = fragment;
        this.pack = pack;
        this.currentAccount = currentAccount;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        iconView = new BackupImageView(context);
        iconView.setRoundRadius(AndroidUtilities.dp(12));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        iconView.setBackground(iconBg);
        header.addView(iconView, LayoutHelper.createLinear(56, 56, 0, 0, 0, 12, 0));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(textColumn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        titleView = new TextView(context);
        titleView.setTextSize(17);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textColumn.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        categoryView = new TextView(context);
        categoryView.setTextSize(13);
        categoryView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        textColumn.addView(categoryView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        statsView = new TextView(context);
        statsView.setTextSize(13);
        statsView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        textColumn.addView(statsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        FrameLayout gridFrame = new FrameLayout(context);
        root.addView(gridFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 260));

        gridView = new RecyclerListView(context);
        GridLayoutManager layoutManager = new GridLayoutManager(context, 4);
        gridView.setLayoutManager(layoutManager);
        gridView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        gridView.setClipToPadding(false);
        StickerAdapter adapter = new StickerAdapter();
        gridView.setAdapter(adapter);
        gridFrame.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new RadialProgressView(context);
        gridFrame.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        errorView = new TextView(context);
        errorView.setTextSize(14);
        errorView.setGravity(Gravity.CENTER);
        errorView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        errorView.setText(LocaleController.getString(R.string.AddStickersNotFound));
        errorView.setVisibility(View.GONE);
        gridFrame.addView(errorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        actionButton = new TextView(context);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setTextSize(14);
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setVisibility(View.GONE);
        root.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 12, 16, 8));

        applyPackHeader();
        setCustomView(root);
        loadStickerSet();
    }

    private void applyPackHeader() {
        String title = pack.getDisplayTitle();
        titleView.setText(title != null ? title : "…");
        categoryView.setText(pack.category != null ? pack.category : "");
        categoryView.setVisibility(pack.category != null && !pack.category.isEmpty() ? View.VISIBLE : View.GONE);

        StringBuilder stats = new StringBuilder();
        if (pack.ratingAvg > 0) {
            stats.append(String.format(LocaleController.getInstance().getCurrentLocale(), "★ %.1f", pack.ratingAvg));
            if (pack.ratingCount > 0) {
                stats.append(" (").append(pack.ratingCount).append(")");
            }
        }
        if (pack.downloads > 0) {
            if (stats.length() > 0) stats.append("  ·  ");
            stats.append(pack.downloads).append(" ↓");
        }
        statsView.setText(stats.toString());
        statsView.setVisibility(stats.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void loadStickerSet() {
        TLRPC.TL_messages_stickerSet cached = StickerSetResolver.getCached(pack.shortName);
        if (cached != null) {
            onStickerSetResolved(cached);
            return;
        }
        progressView.setVisibility(View.VISIBLE);
        StickerSetResolver.resolve(currentAccount, pack.shortName, new StickerSetResolver.Callback() {
            @Override
            public void onResolved(TLRPC.TL_messages_stickerSet result) {
                onStickerSetResolved(result);
            }

            @Override
            public void onError() {
                progressView.setVisibility(View.GONE);
                errorView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void onStickerSetResolved(TLRPC.TL_messages_stickerSet result) {
        stickerSet = result;
        pack.resolvedSet = result;
        progressView.setVisibility(View.GONE);
        applyPackHeader();
        applyPackIcon();
        ((StickerAdapter) gridView.getAdapter()).notifyDataSetChanged();
        updateActionButton();
    }

    private void applyPackIcon() {
        TLRPC.Document first = pack.getFirstSticker();
        if (stickerSet != null && stickerSet.set != null && !stickerSet.set.thumbs.isEmpty()) {
            TLRPC.PhotoSize thumb = org.telegram.messenger.FileLoader.getClosestPhotoSizeWithSize(stickerSet.set.thumbs, 90);
            iconView.setImage(org.telegram.messenger.ImageLocation.getForSticker(thumb, first, stickerSet.set.thumb_version), "100_100", (android.graphics.drawable.Drawable) null, stickerSet);
        } else if (first != null) {
            var svgThumb = org.telegram.messenger.DocumentObject.getSvgThumb(first, Theme.key_windowBackgroundGray, 1.0f);
            iconView.setImage(org.telegram.messenger.ImageLocation.getForDocument(first), "100_100", svgThumb, stickerSet);
        }
    }

    private void updateActionButton() {
        if (stickerSet == null || stickerSet.set == null) {
            actionButton.setVisibility(View.GONE);
            return;
        }
        boolean installed = MediaDataController.getInstance(currentAccount).isStickerPackInstalled(stickerSet.set.id);
        int count = stickerSet.documents != null ? stickerSet.documents.size() : 0;
        actionButton.setVisibility(View.VISIBLE);
        if (installed) {
            actionButton.setText(LocaleController.formatPluralString("RemoveManyStickersCount", count));
            actionButton.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            actionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_windowBackgroundGray), Theme.getColor(Theme.key_listSelector)));
            actionButton.setOnClickListener(v -> removeStickerSet());
        } else {
            actionButton.setText(LocaleController.formatPluralString("AddManyStickersCount", count));
            actionButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            actionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            actionButton.setOnClickListener(v -> installStickerSet());
        }
    }

    private void installStickerSet() {
        if (stickerSet == null || stickerSet.set == null) return;
        actionButton.setEnabled(false);
        TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
        TLRPC.TL_inputStickerSetShortName inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
        inputStickerSet.short_name = pack.shortName;
        req.stickerset = inputStickerSet;
        final int type = stickerSet.set.masks ? MediaDataController.TYPE_MASK
                : stickerSet.set.emojis ? MediaDataController.TYPE_EMOJIPACKS
                : MediaDataController.TYPE_IMAGE;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            actionButton.setEnabled(true);
            if (error == null) {
                MediaDataController.getInstance(currentAccount).loadStickers(type, false, true);
                dismiss();
            } else {
                FileLog.e("StickerPackPreviewSheet: install failed " + error.text);
            }
        }));
    }

    private void removeStickerSet() {
        if (stickerSet == null) return;
        actionButton.setEnabled(false);
        MediaDataController.getInstance(currentAccount).toggleStickerSet(getContext(), stickerSet, 0, fragment, true, true);
        dismiss();
    }

    private class StickerAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public int getItemCount() {
            return stickerSet != null && stickerSet.documents != null ? stickerSet.documents.size() : 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            StickerEmojiCell cell = new StickerEmojiCell(parent.getContext(), false, null);
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(80)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TLRPC.Document document = stickerSet.documents.get(position);
            ((StickerEmojiCell) holder.itemView).setSticker(document, stickerSet, false);
        }
    }
}
