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
    private final TextView ratingView;
    private final TextView downloadsView;

    private StickerPackInfo currentPack;
    private int currentAccount;

    public StickerPackGridCell(Context context) {
        super(context);
        setBackground(GroupCardHelper.cardBackground(context, GroupCardHelper.POS_SINGLE));
        setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        iconView = new BackupImageView(context);
        iconView.setRoundRadius(AndroidUtilities.dp(14));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(AndroidUtilities.dp(14));
        iconBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        iconView.setBackground(iconBg);
        column.addView(iconView, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        titleView = new TextView(context);
        titleView.setTextSize(14);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        categoryView = new TextView(context);
        categoryView.setTextSize(12);
        categoryView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        categoryView.setGravity(Gravity.CENTER_HORIZONTAL);
        categoryView.setMaxLines(1);
        categoryView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(categoryView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 4));

        LinearLayout statsRow = new LinearLayout(context);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(statsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ratingView = new TextView(context);
        ratingView.setTextSize(12);
        ratingView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statsRow.addView(ratingView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8, 0));

        downloadsView = new TextView(context);
        downloadsView.setTextSize(12);
        downloadsView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statsRow.addView(downloadsView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        setWillNotDraw(false);
    }

    public void setPack(StickerPackInfo pack, int currentAccount) {
        this.currentPack = pack;
        this.currentAccount = currentAccount;

        categoryView.setText(pack.category != null ? pack.category : "");
        categoryView.setVisibility(pack.category != null && !pack.category.isEmpty() ? View.VISIBLE : View.GONE);

        if (pack.ratingAvg > 0) {
            ratingView.setText(pack.ratingCount > 0
                    ? String.format(LocaleController.getInstance().getCurrentLocale(), "★ %.1f (%d)", pack.ratingAvg, pack.ratingCount)
                    : String.format(LocaleController.getInstance().getCurrentLocale(), "★ %.1f", pack.ratingAvg));
            ratingView.setVisibility(View.VISIBLE);
        } else {
            ratingView.setVisibility(View.GONE);
        }
        downloadsView.setText(pack.downloads > 0 ? formatDownloads(pack.downloads) : "");
        downloadsView.setVisibility(pack.downloads > 0 ? View.VISIBLE : View.GONE);

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

    private static String formatDownloads(int downloads) {
        if (downloads >= 1000) {
            return String.format(LocaleController.getInstance().getCurrentLocale(), "%.1fK ↓", downloads / 1000f);
        }
        return downloads + " ↓";
    }
}
