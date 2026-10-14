package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import tw.nekomimi.nekogram.settings.GroupCardHelper;

/**
 * Our own full-screen sticker pack detail/preview screen - opened by tapping a grid card
 * in the Sticker Store, replacing Telegram's built-in "add sticker" popup entirely (not a
 * dialog/bottom sheet, a real navigated-to screen with our own layout).
 */
public class StickerPackDetailActivity extends BaseFragment {

    private static final int GRID_COLUMNS = 4;
    private static final int GRID_CELL_DP = 80;
    private static final int GRID_PADDING_DP = 8; // top+bottom padding applied to gridView, each side
    private static final int GRID_MIN_HEIGHT_DP = 160; // used while loading/on error, before the real count is known

    private final StickerPackInfo pack;

    private BackupImageView iconView;
    private TextView titleView;
    private TextView categoryView;
    private TextView installedBadge;
    private LinearLayout starsRow;
    private TextView ratingNumberView;
    private TextView ownerView;
    private TextView ratingsCountView;
    private TextView likesCountView;
    private TextView downloadsCountView;
    private FrameLayout gridFrame;
    private RecyclerListView gridView;
    private RadialProgressView progressView;
    private TextView errorView;
    private TextView actionButton;

    private TLRPC.TL_messages_stickerSet stickerSet;

    public StickerPackDetailActivity(StickerPackInfo pack) {
        super();
        this.pack = pack;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Sticker Pack");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        fragmentView = scrollView;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(root, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // Header: icon + display name + category + installed badge.
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(16));
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        iconView = new BackupImageView(context);
        iconView.setRoundRadius(AndroidUtilities.dp(20));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(AndroidUtilities.dp(20));
        iconBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        iconView.setBackground(iconBg);
        header.addView(iconView, LayoutHelper.createLinear(96, 96, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        titleView = new TextView(context);
        titleView.setTextSize(20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        categoryView = new TextView(context);
        categoryView.setTextSize(14);
        categoryView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        categoryView.setGravity(Gravity.CENTER);
        header.addView(categoryView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 0));

        installedBadge = new TextView(context);
        installedBadge.setText("✓ Already Installed");
        installedBadge.setTextSize(12);
        installedBadge.setTypeface(AndroidUtilities.bold());
        installedBadge.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
        installedBadge.setGravity(Gravity.CENTER);
        installedBadge.setVisibility(View.GONE);
        header.addView(installedBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        // Stats card: owner, average rating (stars), rating count, likes, downloads.
        LinearLayout statsCard = new LinearLayout(context);
        statsCard.setOrientation(LinearLayout.VERTICAL);
        statsCard.setBackground(GroupCardHelper.cardBackground(context, GroupCardHelper.POS_SINGLE));
        statsCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        LinearLayout.LayoutParams statsCardParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        statsCardParams.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        root.addView(statsCard, statsCardParams);

        LinearLayout ratingRow = new LinearLayout(context);
        ratingRow.setOrientation(LinearLayout.HORIZONTAL);
        // Centered as a group (not just vertically) so the stars + number sit in the
        // middle of the card instead of hugging the left edge.
        ratingRow.setGravity(Gravity.CENTER);
        statsCard.addView(ratingRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        starsRow = new LinearLayout(context);
        starsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 5; i++) {
            TextView star = new TextView(context);
            star.setTextSize(18);
            star.setText("★");
            starsRow.addView(star, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 2, 0));
        }
        ratingRow.addView(starsRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        ratingNumberView = new TextView(context);
        ratingNumberView.setTextSize(15);
        ratingNumberView.setTypeface(AndroidUtilities.bold());
        ratingNumberView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        ratingRow.addView(ratingNumberView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        ownerView = createStatRow(context, statsCard, "Owner");
        ratingsCountView = createStatRow(context, statsCard, "Ratings");
        likesCountView = createStatRow(context, statsCard, "Likes");
        downloadsCountView = createStatRow(context, statsCard, "Downloads");

        // Real sticker grid. This sits inside the screen's outer ScrollView, so instead of
        // boxing it to a fixed height (which either cut the last row off or left dead
        // space, and fought the outer scroll with its own internal one), its height is
        // computed from the actual sticker count once the set loads (see updateGridHeight())
        // so the card always extends exactly to fit all the stickers - never clipped, never
        // short.
        gridFrame = new FrameLayout(context);
        LinearLayout.LayoutParams gridFrameParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, GRID_MIN_HEIGHT_DP);
        gridFrameParams.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        root.addView(gridFrame, gridFrameParams);
        gridFrame.setBackground(GroupCardHelper.cardBackground(context, GroupCardHelper.POS_SINGLE));

        gridView = new RecyclerListView(context);
        GridLayoutManager layoutManager = new GridLayoutManager(context, GRID_COLUMNS);
        gridView.setLayoutManager(layoutManager);
        gridView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        gridView.setClipToPadding(false);
        // The outer ScrollView already owns scrolling for the whole screen - the grid's
        // height always matches its content (see updateGridHeight()), so it never needs to
        // scroll on its own.
        gridView.setNestedScrollingEnabled(false);
        gridView.setAdapter(new StickerAdapter());
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
        actionButton.setTextSize(15);
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams actionParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 16, 16, 24);
        root.addView(actionButton, actionParams);

        applyPackHeader();
        loadStickerSet();

        return fragmentView;
    }

    private TextView createStatRow(Context context, LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        row.addView(labelView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(context);
        valueView.setTextSize(14);
        valueView.setTypeface(AndroidUtilities.bold());
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        return valueView;
    }

    private void applyPackHeader() {
        String title = pack.getDisplayTitle();
        titleView.setText(title != null ? title : "…");
        categoryView.setText(pack.category != null ? pack.category : "");
        categoryView.setVisibility(pack.category != null && !pack.category.isEmpty() ? View.VISIBLE : View.GONE);

        setStars(pack.ratingAvg);
        ratingNumberView.setText(pack.ratingAvg > 0
                ? String.format(LocaleController.getInstance().getCurrentLocale(), "%.1f", pack.ratingAvg)
                : "—");

        ownerView.setText(resolveOwnerLabel());
        ratingsCountView.setText(String.valueOf(pack.ratingCount));
        likesCountView.setText(String.valueOf(pack.totalLikes));
        downloadsCountView.setText(String.valueOf(pack.downloads));
    }

    private void setStars(double avg) {
        int filled = (int) Math.round(avg);
        for (int i = 0; i < starsRow.getChildCount(); i++) {
            TextView star = (TextView) starsRow.getChildAt(i);
            star.setTextColor(i < filled
                    ? Theme.getColor(Theme.key_featuredStickers_addButton)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        }
    }

    /** Only resolves from locally cached users - the backend only gives us a numeric
     *  Telegram id (owner_telegram_id), and fetching an arbitrary user by id needs an
     *  access_hash we don't have unless Telegram has already told us about that user. */
    private String resolveOwnerLabel() {
        if (pack.ownerTelegramId == 0) {
            return "Unknown";
        }
        TLRPC.User owner = MessagesController.getInstance(currentAccount).getUser(pack.ownerTelegramId);
        if (owner != null) {
            if (owner.username != null && !owner.username.isEmpty()) {
                return "@" + owner.username;
            }
            String name = UserObject.getUserName(owner);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return "Unknown";
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
        if (fragmentView == null) {
            return;
        }
        stickerSet = result;
        pack.resolvedSet = result;
        progressView.setVisibility(View.GONE);
        applyPackHeader();
        applyPackIcon();
        if (gridView.getAdapter() != null) {
            gridView.getAdapter().notifyDataSetChanged();
        }
        updateGridHeight();
        updateActionButton();
    }

    /** Sizes the grid card to exactly fit every sticker (full rows of GRID_COLUMNS), so it
     *  always extends properly to the bottom instead of being clipped by - or leaving dead
     *  space below - a fixed-height box. */
    private void updateGridHeight() {
        int count = stickerSet != null && stickerSet.documents != null ? stickerSet.documents.size() : 0;
        int heightDp;
        if (count <= 0) {
            heightDp = GRID_MIN_HEIGHT_DP;
        } else {
            int rows = (count + GRID_COLUMNS - 1) / GRID_COLUMNS;
            heightDp = rows * GRID_CELL_DP + GRID_PADDING_DP * 2;
        }
        ViewGroup.LayoutParams params = gridFrame.getLayoutParams();
        params.height = AndroidUtilities.dp(heightDp);
        gridFrame.setLayoutParams(params);
    }

    private void applyPackIcon() {
        TLRPC.Document first = pack.getFirstSticker();
        if (stickerSet != null && stickerSet.set != null && !stickerSet.set.thumbs.isEmpty()) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(stickerSet.set.thumbs, 160);
            iconView.setImage(ImageLocation.getForSticker(thumb, first, stickerSet.set.thumb_version), "160_160", (android.graphics.drawable.Drawable) null, stickerSet);
        } else if (first != null) {
            var svgThumb = DocumentObject.getSvgThumb(first, Theme.key_windowBackgroundGray, 1.0f);
            iconView.setImage(ImageLocation.getForDocument(first), "160_160", svgThumb, stickerSet);
        }
    }

    /** Bug fix: always re-check real install state from MediaDataController - the button
     *  must never default to "Add" when the pack is already installed. */
    private void updateActionButton() {
        if (stickerSet == null || stickerSet.set == null) {
            actionButton.setVisibility(View.GONE);
            installedBadge.setVisibility(View.GONE);
            return;
        }
        boolean installed = MediaDataController.getInstance(currentAccount).isStickerPackInstalled(stickerSet.set.id);
        int count = stickerSet.documents != null ? stickerSet.documents.size() : 0;
        actionButton.setVisibility(View.VISIBLE);
        installedBadge.setVisibility(installed ? View.VISIBLE : View.GONE);
        if (installed) {
            actionButton.setText(LocaleController.formatPluralString("RemoveManyStickersCount", count));
            actionButton.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            actionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_windowBackgroundGray), Theme.getColor(Theme.key_listSelector)));
            actionButton.setOnClickListener(v -> removeStickerSet());
        } else {
            actionButton.setText(LocaleController.formatPluralString("AddManyStickersCount", count));
            actionButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            actionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            actionButton.setOnClickListener(v -> installStickerSet());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // The install state can change elsewhere (another screen, another device) while
        // this one is backgrounded - re-check it rather than trusting whatever it showed
        // when the screen first opened.
        if (stickerSet != null) {
            updateActionButton();
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
                updateActionButton();
            } else {
                FileLog.e("StickerPackDetailActivity: install failed " + error.text);
            }
        }));
    }

    private void removeStickerSet() {
        if (stickerSet == null) return;
        actionButton.setEnabled(false);
        MediaDataController.getInstance(currentAccount).toggleStickerSet(getContext(), stickerSet, 0, this, true, true);
        AndroidUtilities.runOnUIThread(() -> {
            actionButton.setEnabled(true);
            updateActionButton();
        }, 300);
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
