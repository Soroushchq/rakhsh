package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AmbergramProxyManager;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * AmberGram fork: dedicated page for the in-app free proxy list.
 * Lists proxies fetched from public GitHub collectors, shows live
 * availability/ping and connects through the official proxy system.
 * Opened from AmberGram Settings to keep the settings hub clean.
 */
public class AmbergramProxyActivity extends BaseFragment implements AmbergramProxyManager.Listener {

    private static final int BRAND_ORANGE = 0xFFF97316;
    private static final int STATE_GREEN = 0xFF27B434;
    private static final int STATE_RED = 0xFFE13B47;

    private static final int MAX_VISIBLE_ROWS = 90;

    private LinearLayout listContainer;
    private TextView statusTextView;
    private TextView refreshButton;
    private TextView testButton;
    private TextView emptyTextView;
    private boolean updateScheduled;

    @Override
    public boolean onFragmentCreate() {
        AmbergramProxyManager.getInstance().addListener(this);
        AmbergramProxyManager manager = AmbergramProxyManager.getInstance();
        if (manager.getLastFetchAt() == 0 && !manager.isFetching()) {
            manager.runUpdateCycle();
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        AmbergramProxyManager.getInstance().removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.AmbergramProxySection));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        ThemeBind.backgroundColor(fragmentView, Theme.key_windowBackgroundGray);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        ((FrameLayout) fragmentView).addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout contentWrapper = new LinearLayout(context);
        contentWrapper.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentWrapper, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        ThemeBind.backgroundColor(card, Theme.key_windowBackgroundWhite);
        contentWrapper.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        statusTextView = new TextView(context);
        statusTextView.setTypeface(AndroidUtilities.bold());
        statusTextView.setTextSize(13);
        ThemeBind.textColor(statusTextView, Theme.key_windowBackgroundWhiteGrayText2, null);
        statusTextView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(12), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        card.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        buttonsRow.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        card.addView(buttonsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        refreshButton = makeTextButton(context, LocaleController.getString(R.string.AmbergramProxyRefresh), BRAND_ORANGE);
        refreshButton.setOnClickListener(v -> AmbergramProxyManager.getInstance().runUpdateCycle());
        buttonsRow.addView(refreshButton, LayoutHelper.createLinear(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        testButton = makeTextButton(context, LocaleController.getString(R.string.AmbergramProxyTestAll), BRAND_ORANGE);
        testButton.setOnClickListener(v -> AmbergramProxyManager.getInstance().startTesting());
        buttonsRow.addView(testButton, LayoutHelper.createLinear(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyTextView = new TextView(context);
        emptyTextView.setTypeface(AndroidUtilities.bold());
        emptyTextView.setTextSize(14);
        ThemeBind.textColor(emptyTextView, Theme.key_windowBackgroundWhiteGrayText2, null);
        emptyTextView.setGravity(Gravity.CENTER);
        emptyTextView.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(24), AndroidUtilities.dp(28), AndroidUtilities.dp(28));
        card.addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(listContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextInfoPrivacyCell footerCell = new TextInfoPrivacyCell(context, 24, null);
        footerCell.setText(LocaleController.getString(R.string.AmbergramProxyHint));
        contentWrapper.addView(footerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        refreshList();
        return fragmentView;
    }

    private TextView makeTextButton(Context context, String text, int color) {
        TextView button = new TextView(context);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextSize(15);
        button.setTextColor(color);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        return button;
    }

    private View makeProxyRow(Context context, final AmbergramProxyManager.ProxyItem item, boolean showDivider) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        row.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        row.setOnClickListener(v -> {
            AmbergramProxyManager.getInstance().connectTo(item);
            if (getParentActivity() != null) {
                Toast.makeText(getParentActivity(),
                        LocaleController.formatString(R.string.AmbergramProxyConnected, item.host),
                        Toast.LENGTH_SHORT).show();
            }
        });
        row.setOnLongClickListener(v -> {
            try {
                AndroidUtilities.addToClipboard(item.toSettings().getLink());
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(),
                            LocaleController.getString(R.string.AmbergramProxyCopied),
                            Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignore) {
            }
            return true;
        });

        LinearLayout topLine = new LinearLayout(context);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(topLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView hostView = new TextView(context);
        hostView.setTypeface(AndroidUtilities.bold());
        hostView.setTextSize(15);
        ThemeBind.textColor(hostView, Theme.key_windowBackgroundWhiteBlackText, null);
        hostView.setText(item.host + ":" + item.port);
        hostView.setLines(1);
        hostView.setMaxLines(1);
        hostView.setSingleLine(true);
        hostView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        topLine.addView(hostView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView connectView = makeTextButton(context, LocaleController.getString(R.string.AmbergramProxyConnect), BRAND_ORANGE);
        topLine.addView(connectView, LayoutHelper.createLinear(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView statusView = new TextView(context);
        statusView.setTypeface(AndroidUtilities.bold());
        statusView.setTextSize(13);
        statusView.setText(itemStatusText(item));
        statusView.setTextColor(itemStatusColor(item));
        row.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, AndroidUtilities.dp(3), 0, 0));

        if (showDivider) {
            View divider = new View(context);
            divider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            row.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, AndroidUtilities.dp(4), 0, 0));
        }
        return row;
    }

    private String itemStatusText(AmbergramProxyManager.ProxyItem item) {
        String source = LocaleController.formatString(R.string.AmbergramProxySource, item.source);
        if (item.checking) {
            return source;
        }
        if (item.available && item.ping >= 0) {
            return source + "  •  " + LocaleController.formatString(R.string.AmbergramProxyWorking, String.valueOf(item.ping));
        }
        if (item.deadStreak > 0) {
            return source + "  •  " + LocaleController.getString(R.string.AmbergramProxyDead);
        }
        return source + "  •  " + LocaleController.getString(R.string.AmbergramProxyUntested);
    }

    private int itemStatusColor(AmbergramProxyManager.ProxyItem item) {
        if (item.checking) {
            return BRAND_ORANGE;
        }
        if (item.available && item.ping >= 0) {
            return item.ping < 800 ? STATE_GREEN : BRAND_ORANGE;
        }
        if (item.deadStreak > 0) {
            return STATE_RED;
        }
        return getThemedColor(Theme.key_windowBackgroundWhiteGrayText2);
    }

    private String relativeTime(long timestamp) {
        if (timestamp <= 0) {
            return LocaleController.getString(R.string.AmbergramProxyTimeNever);
        }
        long minutes = (System.currentTimeMillis() - timestamp) / 60000L;
        if (minutes < 1) {
            return LocaleController.getString(R.string.AmbergramProxyTimeNow);
        }
        if (minutes < 60) {
            return LocaleController.formatString(R.string.AmbergramProxyTimeMinutes, String.valueOf(minutes));
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return LocaleController.formatString(R.string.AmbergramProxyTimeHours, String.valueOf(hours));
        }
        return LocaleController.formatString(R.string.AmbergramProxyTimeDays, String.valueOf(hours / 24));
    }

    private String buildStatusText() {
        AmbergramProxyManager manager = AmbergramProxyManager.getInstance();
        if (manager.isFetching()) {
            return LocaleController.getString(R.string.AmbergramProxyFetching);
        }
        if (manager.isTesting()) {
            return LocaleController.formatString(R.string.AmbergramProxyTesting,
                    String.valueOf(manager.getProcessedCount()), String.valueOf(manager.getTestTotal()));
        }
        int total = manager.getTotalCount();
        if (total == 0) {
            return LocaleController.getString(R.string.AmbergramProxyEmpty);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(LocaleController.formatString(R.string.AmbergramProxyLastUpdate, relativeTime(manager.getLastFetchAt())));
        builder.append("  •  ");
        builder.append(LocaleController.formatString(R.string.AmbergramProxyStats,
                String.valueOf(manager.getWorkingCount()), String.valueOf(total)));
        if (manager.getFailedSourceCount() > 0) {
            builder.append("  •  ");
            builder.append(LocaleController.formatString(R.string.AmbergramProxyFailedSources,
                    String.valueOf(manager.getFailedSourceCount())));
        }
        return builder.toString();
    }

    private void refreshList() {
        if (listContainer == null || listContainer.getContext() == null) {
            return;
        }
        Context context = listContainer.getContext();
        AmbergramProxyManager manager = AmbergramProxyManager.getInstance();
        ArrayList<AmbergramProxyManager.ProxyItem> items = manager.getProxiesSnapshot();

        listContainer.removeAllViews();
        boolean busy = manager.isFetching() || manager.isTesting();
        boolean empty = items.isEmpty();

        emptyTextView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            emptyTextView.setText(manager.isFetching()
                    ? LocaleController.getString(R.string.AmbergramProxyFetching)
                    : LocaleController.getString(R.string.AmbergramProxyEmpty));
        }

        int visible = Math.min(items.size(), MAX_VISIBLE_ROWS);
        for (int a = 0; a < visible; a++) {
            listContainer.addView(makeProxyRow(context, items.get(a), a != visible - 1));
        }

        if (visible > 0 && items.size() > visible) {
            TextView moreView = new TextView(context);
            moreView.setTypeface(AndroidUtilities.bold());
            moreView.setTextSize(13);
            ThemeBind.textColor(moreView, Theme.key_windowBackgroundWhiteGrayText2, null);
            moreView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(6), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
            moreView.setText(LocaleController.formatString(R.string.AmbergramProxyShowMore,
                    String.valueOf(visible), String.valueOf(items.size())));
            listContainer.addView(moreView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        if (statusTextView != null) {
            statusTextView.setText(buildStatusText());
        }

        if (refreshButton != null) {
            refreshButton.setEnabled(!manager.isFetching());
            refreshButton.setAlpha(manager.isFetching() ? 0.45f : 1f);
        }
        if (testButton != null) {
            testButton.setEnabled(!busy && !empty);
            testButton.setAlpha(busy || empty ? 0.45f : 1f);
        }
    }

    @Override
    public void onProxyEvent(int event, int done, int total) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!updateScheduled) {
                updateScheduled = true;
                AndroidUtilities.runOnUIThread(() -> {
                    updateScheduled = false;
                    refreshList();
                }, 300);
            }
        });
    }
}
