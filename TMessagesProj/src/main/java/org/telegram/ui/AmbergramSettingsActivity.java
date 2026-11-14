package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AmbergramProxyManager;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadQueueController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * AmberGram fork: settings hub for AmberGram features.
 * The sequential download queue lives on its own page (AmbergramQueueActivity)
 * so this hub stays clean and has room for upcoming features.
 */
public class AmbergramSettingsActivity extends BaseFragment implements DownloadQueueController.QueueListener, AmbergramProxyManager.Listener {

    private static final int BRAND_ORANGE = 0xFFF97316;

    private TextView queueValueView;
    private TextView proxyValueView;
    private boolean updateScheduled;
    private boolean proxyUpdateScheduled;

    @Override
    public boolean onFragmentCreate() {
        DownloadQueueController.getInstance(currentAccount).addQueueListener(this);
        AmbergramProxyManager.getInstance().addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        DownloadQueueController.getInstance(currentAccount).removeQueueListener(this);
        AmbergramProxyManager.getInstance().removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.AmbergramSettings));
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

        LinearLayout queueRow = makeQueueRow(context);
        card.addView(queueRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View queueDivider = new View(context);
        queueDivider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        card.addView(queueDivider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));

        LinearLayout proxyRow = makeProxyRow(context);
        card.addView(proxyRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View proxyDivider = new View(context);
        proxyDivider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        card.addView(proxyDivider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));

        TextInfoPrivacyCell footerCell = new TextInfoPrivacyCell(context, 24, null);
        footerCell.setText(LocaleController.getString(R.string.AmbergramQueueComingSoon));
        contentWrapper.addView(footerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        refreshQueueValue();
        refreshProxyValue();
        return fragmentView;
    }

    private LinearLayout makeQueueRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        row.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        row.setOnClickListener(v -> presentFragment(new AmbergramQueueActivity()));

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.msg_download);
        iconView.setColorFilter(BRAND_ORANGE, PorterDuff.Mode.SRC_IN);
        row.addView(iconView, LayoutHelper.createLinear(AndroidUtilities.dp(26), AndroidUtilities.dp(26), 0, Gravity.CENTER_VERTICAL, 0, 0, AndroidUtilities.dp(14), 0));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(16);
        ThemeBind.textColor(titleView, Theme.key_windowBackgroundWhiteBlackText, null);
        titleView.setText(LocaleController.getString(R.string.AmbergramQueueSection));
        titleView.setSingleLine(true);
        textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        queueValueView = new TextView(context);
        queueValueView.setTypeface(AndroidUtilities.bold());
        queueValueView.setTextSize(13);
        ThemeBind.textColor(queueValueView, Theme.key_windowBackgroundWhiteGrayText2, null);
        queueValueView.setSingleLine(true);
        textCol.addView(queueValueView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView chevronView = new TextView(context);
        chevronView.setTypeface(AndroidUtilities.bold());
        chevronView.setTextSize(20);
        ThemeBind.textColor(chevronView, Theme.key_windowBackgroundWhiteGrayText2, null);
        chevronView.setText(LocaleController.isRTL ? "‹" : "›");
        chevronView.setGravity(Gravity.CENTER);
        row.addView(chevronView, LayoutHelper.createLinear(AndroidUtilities.dp(28), ViewGroup.LayoutParams.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL, AndroidUtilities.dp(8), 0, 0, 0));

        return row;
    }

    private void refreshQueueValue() {
        if (queueValueView == null || getContext() == null) {
            return;
        }
        ArrayList<DownloadQueueController.QueueItem> items = DownloadQueueController.getInstance(currentAccount).getQueueSnapshot();
        int waiting = 0, downloading = 0;
        for (int a = 0; a < items.size(); a++) {
            DownloadQueueController.QueueItem item = items.get(a);
            if (item.state == DownloadQueueController.STATE_PENDING) {
                waiting++;
            } else if (item.state == DownloadQueueController.STATE_DOWNLOADING) {
                downloading++;
            }
        }
        if (waiting > 0 || downloading > 0) {
            queueValueView.setText(LocaleController.formatString(R.string.AmbergramQueueSummary, String.valueOf(waiting), String.valueOf(downloading)));
            queueValueView.setTextColor(BRAND_ORANGE);
        } else {
            queueValueView.setText(LocaleController.getString(R.string.AmbergramQueueRowIdle));
            queueValueView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        }
    }

    private LinearLayout makeProxyRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        row.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        row.setOnClickListener(v -> presentFragment(new AmbergramProxyActivity()));

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.ic_ambergram_proxy);
        iconView.setColorFilter(BRAND_ORANGE, PorterDuff.Mode.SRC_IN);
        row.addView(iconView, LayoutHelper.createLinear(AndroidUtilities.dp(26), AndroidUtilities.dp(26), 0, Gravity.CENTER_VERTICAL, 0, 0, AndroidUtilities.dp(14), 0));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(16);
        ThemeBind.textColor(titleView, Theme.key_windowBackgroundWhiteBlackText, null);
        titleView.setText(LocaleController.getString(R.string.AmbergramProxySection));
        titleView.setSingleLine(true);
        textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        proxyValueView = new TextView(context);
        proxyValueView.setTypeface(AndroidUtilities.bold());
        proxyValueView.setTextSize(13);
        ThemeBind.textColor(proxyValueView, Theme.key_windowBackgroundWhiteGrayText2, null);
        proxyValueView.setSingleLine(true);
        textCol.addView(proxyValueView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView chevronView = new TextView(context);
        chevronView.setTypeface(AndroidUtilities.bold());
        chevronView.setTextSize(20);
        ThemeBind.textColor(chevronView, Theme.key_windowBackgroundWhiteGrayText2, null);
        chevronView.setText(LocaleController.isRTL ? "‹" : "›");
        chevronView.setGravity(Gravity.CENTER);
        row.addView(chevronView, LayoutHelper.createLinear(AndroidUtilities.dp(28), ViewGroup.LayoutParams.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL, AndroidUtilities.dp(8), 0, 0, 0));

        return row;
    }

    private void refreshProxyValue() {
        if (proxyValueView == null || getContext() == null) {
            return;
        }
        AmbergramProxyManager manager = AmbergramProxyManager.getInstance();
        if (manager.isFetching()) {
            proxyValueView.setText(LocaleController.getString(R.string.AmbergramProxyFetching));
            proxyValueView.setTextColor(BRAND_ORANGE);
            return;
        }
        if (manager.isTesting()) {
            proxyValueView.setText(LocaleController.formatString(R.string.AmbergramProxyTesting, String.valueOf(manager.getProcessedCount()), String.valueOf(manager.getTestTotal())));
            proxyValueView.setTextColor(BRAND_ORANGE);
            return;
        }
        int working = manager.getWorkingCount();
        if (working > 0) {
            proxyValueView.setText(LocaleController.formatString(R.string.AmbergramProxySettingsValue, String.valueOf(working)));
            proxyValueView.setTextColor(BRAND_ORANGE);
        } else {
            proxyValueView.setText(LocaleController.getString(R.string.AmbergramProxySubtitle));
            proxyValueView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        }
    }

    @Override
    public void onProxyEvent(int event, int done, int total) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!proxyUpdateScheduled) {
                proxyUpdateScheduled = true;
                AndroidUtilities.runOnUIThread(() -> {
                    proxyUpdateScheduled = false;
                    refreshProxyValue();
                }, 300);
            }
        });
    }

    @Override
    public void onQueueUpdated() {
        AndroidUtilities.runOnUIThread(() -> {
            if (!updateScheduled) {
                updateScheduled = true;
                AndroidUtilities.runOnUIThread(() -> {
                    updateScheduled = false;
                    refreshQueueValue();
                }, 300);
            }
        });
    }
}
