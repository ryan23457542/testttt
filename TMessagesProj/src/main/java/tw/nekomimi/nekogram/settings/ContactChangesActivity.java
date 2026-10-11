package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;

import static org.telegram.messenger.AndroidUtilities.dp;

public class ContactChangesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_CLEAR = 1;

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private TextView emptyView;

    private final ArrayList<LogEntry> logEntries = new ArrayList<>();

    private static class LogEntry {
        long userId;
        String name;
        String type; // "name" or "photo"
        String oldValue;
        String newValue;
        long time;
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nk_contact_changes", Context.MODE_PRIVATE);
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        loadLog();
        checkForChanges();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Contacts Changes");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_CLEAR) {
                    confirmClearLog();
                }
            }
        });
        actionBar.createMenu().addItem(MENU_CLEAR, R.drawable.msg_delete);

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listAdapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText("No changes recorded yet.\nKurogram will log name and photo changes of your contacts while the app is open.");
        emptyView.setTextSize(15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(60, 0, 60, 0);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        updateEmptyView();

        return fragmentView;
    }

    private void confirmClearLog() {
        if (getParentActivity() == null || logEntries.isEmpty()) {
            return;
        }
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle("Clear Log");
        builder.setMessage("Clear all recorded contact changes?");
        builder.setPositiveButton("Clear", (dialog, which) -> {
            logEntries.clear();
            saveLog();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            updateEmptyView();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateEmptyView() {
        if (emptyView == null || listView == null) {
            return;
        }
        emptyView.setVisibility(logEntries.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(logEntries.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void loadLog() {
        logEntries.clear();
        String saved = prefs().getString("log", "[]");
        try {
            JSONArray arr = new JSONArray(saved);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                LogEntry entry = new LogEntry();
                entry.userId = obj.optLong("uid");
                entry.name = obj.optString("name");
                entry.type = obj.optString("type");
                entry.oldValue = obj.optString("old");
                entry.newValue = obj.optString("new");
                entry.time = obj.optLong("time");
                logEntries.add(entry);
            }
        } catch (JSONException ignored) {
        }
        Collections.reverse(logEntries);
    }

    private void saveLog() {
        JSONArray arr = new JSONArray();
        ArrayList<LogEntry> chronological = new ArrayList<>(logEntries);
        Collections.reverse(chronological);
        int start = Math.max(0, chronological.size() - 200);
        for (int i = start; i < chronological.size(); i++) {
            LogEntry entry = chronological.get(i);
            JSONObject obj = new JSONObject();
            try {
                obj.put("uid", entry.userId);
                obj.put("name", entry.name);
                obj.put("type", entry.type);
                obj.put("old", entry.oldValue);
                obj.put("new", entry.newValue);
                obj.put("time", entry.time);
                arr.put(obj);
            } catch (JSONException ignored) {
            }
        }
        prefs().edit().putString("log", arr.toString()).apply();
    }

    private JSONObject loadSnapshot() {
        String saved = prefs().getString("snapshot", "{}");
        try {
            return new JSONObject(saved);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void saveSnapshot(JSONObject snapshot) {
        prefs().edit().putString("snapshot", snapshot.toString()).apply();
    }

    private void checkForChanges() {
        JSONObject snapshot = loadSnapshot();
        boolean changed = false;

        for (TLRPC.TL_contact contact : ContactsController.getInstance(currentAccount).contacts) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
            if (user == null || UserObject.isDeleted(user) || UserObject.isUserSelf(user)) {
                continue;
            }
            String key = String.valueOf(user.id);
            String currentName = ContactsController.formatName(user.first_name, user.last_name);
            long photoId = (user.photo != null) ? user.photo.photo_id : 0;

            JSONObject prevData = snapshot.optJSONObject(key);
            if (prevData == null) {
                JSONObject newData = new JSONObject();
                try {
                    newData.put("name", currentName);
                    newData.put("photoId", photoId);
                    snapshot.put(key, newData);
                } catch (JSONException ignored) {
                }
                changed = true;
                continue;
            }

            String prevName = prevData.optString("name");
            long prevPhotoId = prevData.optLong("photoId");

            if (!prevName.equals(currentName)) {
                addLogEntry(user.id, currentName, "name", prevName, currentName);
                try {
                    prevData.put("name", currentName);
                } catch (JSONException ignored) {
                }
                changed = true;
            }

            if (prevPhotoId != photoId) {
                addLogEntry(user.id, currentName, "photo", null, null);
                try {
                    prevData.put("photoId", photoId);
                } catch (JSONException ignored) {
                }
                changed = true;
            }
        }

        if (changed) {
            saveSnapshot(snapshot);
            saveLog();
            loadLog();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            updateEmptyView();
        }
    }

    private void addLogEntry(long userId, String name, String type, String oldValue, String newValue) {
        LogEntry entry = new LogEntry();
        entry.userId = userId;
        entry.name = name;
        entry.type = type;
        entry.oldValue = oldValue;
        entry.newValue = newValue;
        entry.time = System.currentTimeMillis();
        logEntries.add(0, entry);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces || id == NotificationCenter.contactsDidLoad) {
            checkForChanges();
        }
    }

    private String formatEntryText(LogEntry entry) {
        if ("photo".equals(entry.type)) {
            return "changed his/her Profile Photo.";
        } else {
            return "changed his/her name from \"" + entry.oldValue + "\" to \"" + entry.newValue + "\".";
        }
    }

    private static String formatDate(long time) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(time);

        boolean sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
        java.util.Locale locale = java.util.Locale.getDefault();
        if (sameDay) {
            return new java.text.SimpleDateFormat("HH:mm", locale).format(new java.util.Date(time));
        }

        long diffDays = (now.getTimeInMillis() - then.getTimeInMillis()) / 86400000L;
        if (diffDays < 7) {
            return new java.text.SimpleDateFormat("EEE", locale).format(new java.util.Date(time));
        }

        boolean sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR);
        if (sameYear) {
            return new java.text.SimpleDateFormat("MMM d", locale).format(new java.util.Date(time));
        }
        return new java.text.SimpleDateFormat("MMM d, yyyy", locale).format(new java.util.Date(time));
    }

    private static class ChangeCell extends FrameLayout {

        private final BackupImageView avatarImageView;
        private final TextView nameTextView;
        private final TextView descriptionTextView;
        private final TextView dateTextView;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();

        ChangeCell(Context context) {
            super(context);

            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(dp(10));
            addView(avatarImageView, LayoutHelper.createFrame(46, 46, Gravity.LEFT | Gravity.TOP, 13, 10, 0, 0));

            dateTextView = new TextView(context);
            dateTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            dateTextView.setTextSize(13);
            dateTextView.setSingleLine(true);
            addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 13, 13, 0));

            // MATCH_PARENT width + matching left/right margins so setSingleLine/setEllipsize
            // actually take effect for long names, instead of growing under the date text.
            nameTextView = new TextView(context);
            nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTextView.setTypeface(Typeface.DEFAULT_BOLD);
            nameTextView.setTextSize(16);
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 75, 11, 70, 0));

            // MATCH_PARENT width + bottom margin so the cell can grow to fit up to 2 lines
            // instead of being clipped by a fixed-height onMeasure.
            descriptionTextView = new TextView(context);
            descriptionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            descriptionTextView.setTextSize(14);
            descriptionTextView.setMaxLines(2);
            descriptionTextView.setLineSpacing(dp(2), 1f);
            descriptionTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(descriptionTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 75, 34, 13, 10));
        }

        void bind(LogEntry entry, int groupPos) {
            TLRPC.User user = MessagesController.getInstance(UserConfigHolder.currentAccount).getUser(entry.userId);
            avatarDrawable.setInfo(UserConfigHolder.currentAccount, user);
            avatarImageView.setForUserOrChat(user, avatarDrawable);
            nameTextView.setText(entry.name);
            descriptionTextView.setText(formatEntryTextStatic(entry));
            dateTextView.setText(formatDate(entry.time));
            GroupCardHelper.applyCard(this, groupPos);
            setWillNotDraw(!GroupCardHelper.needsDivider(groupPos));
        }

        private static String formatEntryTextStatic(LogEntry entry) {
            if ("photo".equals(entry.type)) {
                return "changed his/her Profile Photo.";
            } else {
                return "changed his/her name from \"" + entry.oldValue + "\" to \"" + entry.newValue + "\".";
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            // Let the cell grow to fit its real content (avatar + up to 2 lines of
            // description) instead of forcing a fixed height that clipped longer entries.
            // A minimum height still keeps short single-line entries from feeling cramped
            // and matches the avatar's own footprint (10dp top + 46dp tall + 10dp bottom).
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int contentHeight = getMeasuredHeight();
            int minHeight = dp(66);
            int height = Math.max(contentHeight, minHeight);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            if (!willNotDraw()) {
                canvas.drawLine(dp(75), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    private static class UserConfigHolder {
        static int currentAccount;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
            UserConfigHolder.currentAccount = currentAccount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            return logEntries.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ChangeCell cell = new ChangeCell(context);
            cell.setLayoutParams(GroupCardHelper.freshLayoutParams());
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            LogEntry entry = logEntries.get(position);
            int pos = GroupCardHelper.positionInGroup(position, logEntries.size());
            ((ChangeCell) holder.itemView).bind(entry, pos);
        }
    }
}
