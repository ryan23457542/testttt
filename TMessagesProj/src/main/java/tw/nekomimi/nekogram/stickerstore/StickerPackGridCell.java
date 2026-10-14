package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import tw.nekomimi.nekogram.settings.GroupCardHelper;

public class StickerPackGridCell extends FrameLayout {

    private final BackupImageView iconView;
    private final TextView titleView;
    private final TextView categoryView;
    private final TextView likesView;
    private final TextView ratingView;

    private StickerPackInfo currentPack;
    private int currentAccount;

    public StickerPackGridCell(Context context) {
        super(context);
        setBackground(GroupCardHelper.cardBackground(context, GroupCardHelper.POS_SINGLE));
        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        iconView = new BackupImageView(context);
        iconView.setRoundRadius(AndroidUtilities.dp(12));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        iconView.setBackground(iconBg);
        column.addView(iconView, LayoutHelper.createLinear(58, 58, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        // Display Name only - the technical short_name is never shown anywhere in this cell.
        titleView = new TextView(context);
        titleView.setTextSize(14);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        categoryView = new TextView(context);
        categoryView.setTextSize(11);
        categoryView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        categoryView.setGravity(Gravity.CENTER_HORIZONTAL);
        categoryView.setMaxLines(1);
        categoryView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(categoryView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 6));

        // Bottom stats row: total likes on the bottom-left, average rating on the bottom-right.
        LinearLayout statsRow = new LinearLayout(context);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(statsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        likesView = new TextView(context);
        likesView.setTextSize(12);
        likesView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        likesView.setGravity(Gravity.LEFT);
        statsRow.addView(likesView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        ratingView = new TextView(context);
        ratingView.setTextSize(12);
        ratingView.setTypeface(AndroidUtilities.bold());
        ratingView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        ratingView.setGravity(Gravity.RIGHT);
        statsRow.addView(ratingView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        setWillNotDraw(false);
    }

    public void setPack(StickerPackInfo pack, int currentAccount) {
        this.currentPack = pack;
        this.currentAccount = currentAccount;

        categoryView.setText(pack.category != null ? pack.category : "");
        categoryView.setVisibility(pack.category != null && !pack.category.isEmpty() ? View.VISIBLE : View.GONE);

        likesView.setText(pack.totalLikes > 0 ? formatCount(pack.totalLikes) + " ♥" : "");
        ratingView.setText(pack.ratingAvg > 0
                ? String.format(LocaleController.getInstance().getCurrentLocale(), "★ %.1f", pack.ratingAvg)
                : "");

        applyResolvedTitleAndIcon();

        if (pack.resolvedSet == null) {
            TLRPC.TL_messages_stickerSet cached = StickerSetResolver.getCached(pack.shortName);
            if (cached != null) {
                pack.resolvedSet = cached;
                applyResolvedTitleAndIcon();
            } else {
                StickerSetResolver.resolve(currentAccount, pack.shortName, new StickerSetResolver.Callback() {
                    @Override
                    public void onResolved(TLRPC.TL_messages_stickerSet stickerSet) {
                        if (currentPack == pack) {
                            pack.resolvedSet = stickerSet;
                            applyResolvedTitleAndIcon();
                        }
                    }

                    @Override
                    public void onError() {
                        if (currentPack == pack) {
                            titleView.setText(R.string.AddStickersNotFound);
                        }
                    }
                });
            }
        }
    }

    private void applyResolvedTitleAndIcon() {
        String title = currentPack.getDisplayTitle();
        titleView.setText(title != null ? title : "…");

        TLRPC.Document thumbDocument = currentPack.getFirstSticker();
        if (currentPack.resolvedSet != null && currentPack.resolvedSet.set != null && !currentPack.resolvedSet.set.thumbs.isEmpty()) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(currentPack.resolvedSet.set.thumbs, 90);
            iconView.setImage(org.telegram.messenger.ImageLocation.getForSticker(thumb, thumbDocument, currentPack.resolvedSet.set.thumb_version), "80_80", (android.graphics.drawable.Drawable) null, currentPack.resolvedSet);
        } else if (thumbDocument != null) {
            var svgThumb = DocumentObject.getSvgThumb(thumbDocument, Theme.key_windowBackgroundGray, 1.0f);
            iconView.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumbDocument), "80_80", svgThumb, currentPack.resolvedSet);
        } else {
            iconView.setImageDrawable(null);
        }
    }

    private static String formatCount(int count) {
        if (count >= 1000) {
            return String.format(LocaleController.getInstance().getCurrentLocale(), "%.1fK", count / 1000f);
        }
        return String.valueOf(count);
    }
}
