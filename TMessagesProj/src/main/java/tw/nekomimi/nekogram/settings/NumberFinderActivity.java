package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import android.os.Bundle;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;

/**
 * Phone number lookup against the user's own synced Telegram contacts, styled after
 * Telegram's own phone-login screen (floating-label outline field + full width accent
 * button). Matching works against whatever country code a contact is actually stored
 * with - nothing here assumes or defaults to a particular country.
 */
public class NumberFinderActivity extends BaseFragment {

    private OutlineTextContainerView phoneOutlineView;
    private EditText phoneInput;
    private LinearLayout resultContainer;
    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private TextView usernameTextView;
    private TextView statusTextView;
    private FrameLayout openChatButton;
    private TLRPC.User foundUser;

    /** Below this many matching trailing digits, a suffix match is too weak to trust -
     *  this is what keeps results from crossing between contacts in different countries
     *  that happen to share a short local-number suffix, instead of quietly favoring
     *  whichever country the first matching contact happens to be in. */
    private static final int MIN_MATCH_DIGITS = 7;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Number Finder");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = rootLayout;

        TextView headerText = new TextView(context);
        headerText.setText("Find a contact by their phone number, in any country's format.");
        headerText.setTextSize(14);
        headerText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        headerText.setGravity(Gravity.CENTER);
        headerText.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(20), AndroidUtilities.dp(32), AndroidUtilities.dp(16));
        rootLayout.addView(headerText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Floating-label outline field, same visual language as Telegram's own phone
        // entry screen (LoginActivity), rather than a plain underline EditText.
        phoneOutlineView = new OutlineTextContainerView(context);
        phoneOutlineView.setText("Phone Number");

        phoneInput = new EditText(context);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneInput.setHint("Any country code, e.g. +44, +95, +1…");
        phoneInput.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        phoneInput.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        phoneInput.setTextSize(16);
        phoneInput.setSingleLine(true);
        phoneInput.setBackground(null);
        phoneInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        phoneInput.setOnFocusChangeListener((v, hasFocus) -> phoneOutlineView.animateSelection(hasFocus));
        phoneInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });
        phoneOutlineView.attachEditText(phoneInput);
        phoneOutlineView.addView(phoneInput, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 4, 12, 4));

        LinearLayout.LayoutParams outlineParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56);
        outlineParams.setMargins(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        rootLayout.addView(phoneOutlineView, outlineParams);

        TextView searchButton = new TextView(context);
        searchButton.setText("Search");
        searchButton.setTextColor(0xffffffff);
        searchButton.setTextSize(15);
        searchButton.setTypeface(AndroidUtilities.bold());
        searchButton.setGravity(Gravity.CENTER);
        searchButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        searchButton.setOnClickListener(v -> doSearch());
        LinearLayout.LayoutParams searchButtonParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48);
        searchButtonParams.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), 0);
        rootLayout.addView(searchButton, searchButtonParams);

        // Result container (hidden until search)
        resultContainer = new LinearLayout(context);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        resultContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        resultContainer.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        resultContainer.setVisibility(View.GONE);

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(40));
        resultContainer.addView(avatarImageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL));

        nameTextView = new TextView(context);
        nameTextView.setTextSize(18);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setGravity(Gravity.CENTER);
        nameTextView.setPadding(0, AndroidUtilities.dp(12), 0, 0);
        resultContainer.addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        usernameTextView = new TextView(context);
        usernameTextView.setTextSize(14);
        usernameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        usernameTextView.setGravity(Gravity.CENTER);
        usernameTextView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        resultContainer.addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        openChatButton = new FrameLayout(context);
        openChatButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        TextView openChatText = new TextView(context);
        openChatText.setText("Open Chat");
        openChatText.setTextColor(0xffffffff);
        openChatText.setTextSize(14);
        openChatText.setGravity(Gravity.CENTER);
        openChatButton.addView(openChatText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        openChatButton.setOnClickListener(v -> {
            if (foundUser != null) {
                Bundle args = new Bundle();
                args.putLong("user_id", foundUser.id);
                presentFragment(new ChatActivity(args));
            }
        });
        LinearLayout.LayoutParams btnParams = LayoutHelper.createLinear(160, 40, Gravity.CENTER_HORIZONTAL);
        btnParams.topMargin = AndroidUtilities.dp(16);
        resultContainer.addView(openChatButton, btnParams);

        statusTextView = new TextView(context);
        statusTextView.setTextSize(15);
        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        statusTextView.setVisibility(View.GONE);
        rootLayout.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        rootLayout.addView(resultContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
    }

    private void doSearch() {
        String raw = phoneInput.getText().toString();
        String normalized = raw.replaceAll("[^0-9]", "");
        AndroidUtilities.hideKeyboard(phoneInput);

        resultContainer.setVisibility(View.GONE);
        statusTextView.setVisibility(View.GONE);
        foundUser = null;

        if (TextUtils.isEmpty(normalized)) {
            statusTextView.setText("Please enter a phone number.");
            statusTextView.setVisibility(View.VISIBLE);
            return;
        }

        // Match by longest common trailing-digit run rather than "first contact whose
        // number happens to share any suffix" - that first-match approach is what made
        // the search look biased toward whichever country code the earliest-indexed
        // contacts used. This way a contact stored under +44, +95, +1, or any other
        // country code is matched on equal footing, purely by how much of the number
        // the user actually typed in matches.
        TLRPC.User best = null;
        int bestScore = 0;
        for (TLRPC.TL_contact contact : ContactsController.getInstance(currentAccount).contacts) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
            if (user == null || user.phone == null) {
                continue;
            }
            String userPhone = user.phone.replaceAll("[^0-9]", "");
            if (userPhone.isEmpty()) {
                continue;
            }
            int score = commonSuffixLength(userPhone, normalized);
            boolean fullMatch = score == userPhone.length() || score == normalized.length();
            if (score > bestScore && (fullMatch || score >= MIN_MATCH_DIGITS)) {
                bestScore = score;
                best = user;
            }
        }

        if (best != null) {
            foundUser = best;
            String fullName = ContactsController.formatName(best.first_name, best.last_name);
            nameTextView.setText(fullName);
            if (best.username != null) {
                usernameTextView.setText("@" + best.username);
                usernameTextView.setVisibility(View.VISIBLE);
            } else {
                usernameTextView.setVisibility(View.GONE);
            }
            AvatarDrawable avatarDrawable = new AvatarDrawable(best);
            avatarImageView.setForUserOrChat(best, avatarDrawable);
            resultContainer.setVisibility(View.VISIBLE);
        } else {
            statusTextView.setText("No contact found with this number.\nMake sure the number is saved in your contacts.");
            statusTextView.setVisibility(View.VISIBLE);
        }
    }

    private static int commonSuffixLength(String a, String b) {
        int i = a.length() - 1;
        int j = b.length() - 1;
        int count = 0;
        while (i >= 0 && j >= 0 && a.charAt(i) == b.charAt(j)) {
            count++;
            i--;
            j--;
        }
        return count;
    }
}
