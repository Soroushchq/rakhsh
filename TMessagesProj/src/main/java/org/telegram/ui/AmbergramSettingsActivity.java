package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

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
 * AmberGram fork: dedicated settings page for AmberGram features.
 * Currently shows the sequential download queue (live progress, per-item
 * removal, clear finished, cancel all). More sections will be added later.
 */
public class AmbergramSettingsActivity extends BaseFragment implements DownloadQueueController.QueueListener {

    private static final int BRAND_ORANGE = 0xFFF97316;
    private static final int STATE_GREEN = 0xFF27B434;
    private static final int STATE_RED = 0xFFE13B47;

    private LinearLayout queueContainer;
    private TextView summaryTextView;
    private TextView emptyTextView;
    private boolean updateScheduled;

    @Override
    public boolean onFragmentCreate() {
        DownloadQueueController.getInstance(currentAccount).addQueueListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        DownloadQueueController.getInstance(currentAccount).removeQueueListener(this);
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
        scrollView.setVerticalScrollBarEnabled(false);
        ((FrameLayout) fragmentView).addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout contentWrapper = new LinearLayout(context);
        contentWrapper.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentWrapper, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        ThemeBind.backgroundColor(card, Theme.key_windowBackgroundWhite);
        contentWrapper.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString(R.string.AmbergramQueueSection));
        card.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        summaryTextView = new TextView(context);
        summaryTextView.setTypeface(AndroidUtilities.bold());
        summaryTextView.setTextSize(13);
        ThemeBind.textColor(summaryTextView, Theme.key_windowBackgroundWhiteGrayText2, null);
        summaryTextView.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        summaryTextView.setVisibility(View.GONE);
        card.addView(summaryTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        queueContainer = new LinearLayout(context);
        queueContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(queueContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        emptyTextView = new TextView(context);
        emptyTextView.setTypeface(AndroidUtilities.bold());
        emptyTextView.setTextSize(14);
        ThemeBind.textColor(emptyTextView, Theme.key_windowBackgroundWhiteGrayText2, null);
        emptyTextView.setGravity(Gravity.CENTER);
        emptyTextView.setPadding(AndroidUtilities.dp(28), AndroidUtilities.dp(24), AndroidUtilities.dp(28), AndroidUtilities.dp(28));
        card.addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        buttonsRow.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        card.addView(buttonsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView clearFinishedButton = makeTextButton(context, LocaleController.getString(R.string.AmbergramQueueClearFinished), BRAND_ORANGE);
        clearFinishedButton.setOnClickListener(v -> DownloadQueueController.getInstance(currentAccount).removeFinished());
        buttonsRow.addView(clearFinishedButton, LayoutHelper.createLinear(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView cancelAllButton = makeTextButton(context, LocaleController.getString(R.string.AmberQueueCancelAll), STATE_RED);
        cancelAllButton.setOnClickListener(v -> DownloadQueueController.getInstance(currentAccount).cancelAll());
        buttonsRow.addView(cancelAllButton, LayoutHelper.createLinear(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextInfoPrivacyCell footerCell = new TextInfoPrivacyCell(context, 24, null);
        footerCell.setText(LocaleController.getString(R.string.AmbergramQueueComingSoon));
        contentWrapper.addView(footerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        refreshQueue();
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

    private View makeQueueRow(Context context, final DownloadQueueController.QueueItem item, boolean showDivider) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));

        LinearLayout topLine = new LinearLayout(context);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(topLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView nameView = new TextView(context);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextSize(16);
        ThemeBind.textColor(nameView, Theme.key_windowBackgroundWhiteBlackText, null);
        nameView.setLines(1);
        nameView.setMaxLines(1);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        topLine.addView(nameView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        if (item.state == DownloadQueueController.STATE_PENDING) {
            TextView removeView = makeTextButton(context, LocaleController.getString(R.string.AmbergramQueueRemove), BRAND_ORANGE);
            removeView.setOnClickListener(v -> DownloadQueueController.getInstance(currentAccount).cancelPendingItem(item));
            topLine.addView(removeView, LayoutHelper.createLinear(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView statusView = new TextView(context);
        statusView.setTypeface(AndroidUtilities.bold());
        statusView.setTextSize(13);
        statusView.setText(statusText(item));
        statusView.setTextColor(statusColor(item));
        row.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, AndroidUtilities.dp(3), 0, 0));

        if (item.state == DownloadQueueController.STATE_DOWNLOADING && item.size > 0) {
            ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.getProgressDrawable().setColorFilter(BRAND_ORANGE, PorterDuff.Mode.SRC_IN);
            progressBar.setMax(100);
            progressBar.setProgress((int) Math.min(100, item.loadedBytes * 100 / Math.max(1, item.size)));
            progressBar.setProgressTintMode(PorterDuff.Mode.SRC_IN);
            row.addView(progressBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, AndroidUtilities.dp(4), 0, 0));
        }

        if (showDivider) {
            View divider = new View(context);
            divider.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            row.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, AndroidUtilities.dp(4), 0, 0));
        }
        return row;
    }

    private String statusText(DownloadQueueController.QueueItem item) {
        String size = AndroidUtilities.formatFileSize(item.size);
        switch (item.state) {
            case DownloadQueueController.STATE_DOWNLOADING:
                String progress = item.size > 0 ? AndroidUtilities.formatFileSize(item.loadedBytes) + " / " + size : size;
                return LocaleController.getString(R.string.AmbergramQueueStateDownloading) + " • " + progress;
            case DownloadQueueController.STATE_DONE:
                return LocaleController.getString(R.string.AmbergramQueueStateDone) + " • " + size;
            case DownloadQueueController.STATE_FAILED:
                return LocaleController.getString(R.string.AmbergramQueueStateFailed);
            default:
                return LocaleController.getString(R.string.AmbergramQueueStatePending) + " • " + size;
        }
    }

    private int statusColor(DownloadQueueController.QueueItem item) {
        switch (item.state) {
            case DownloadQueueController.STATE_DOWNLOADING:
                return BRAND_ORANGE;
            case DownloadQueueController.STATE_DONE:
                return STATE_GREEN;
            case DownloadQueueController.STATE_FAILED:
                return STATE_RED;
            default:
                return getThemedColor(Theme.key_windowBackgroundWhiteGrayText2);
        }
    }

    private void refreshQueue() {
        if (queueContainer == null || queueContainer.getContext() == null) {
            return;
        }
        Context context = queueContainer.getContext();
        ArrayList<DownloadQueueController.QueueItem> items = DownloadQueueController.getInstance(currentAccount).getQueueSnapshot();

        queueContainer.removeAllViews();
        boolean empty = items.isEmpty();
        emptyTextView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            emptyTextView.setText(LocaleController.getString(R.string.AmbergramQueueEmpty));
        }

        int waiting = 0, downloading = 0;
        for (int a = 0; a < items.size(); a++) {
            DownloadQueueController.QueueItem item = items.get(a);
            if (item.state == DownloadQueueController.STATE_PENDING) {
                waiting++;
            } else if (item.state == DownloadQueueController.STATE_DOWNLOADING) {
                downloading++;
            }
            queueContainer.addView(makeQueueRow(context, item, a != items.size() - 1));
        }

        if (summaryTextView != null) {
            if (waiting > 0 || downloading > 0) {
                summaryTextView.setText(LocaleController.formatString(R.string.AmbergramQueueSummary, String.valueOf(waiting), String.valueOf(downloading)));
                summaryTextView.setVisibility(View.VISIBLE);
            } else {
                summaryTextView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onQueueUpdated() {
        AndroidUtilities.runOnUIThread(() -> {
            if (!updateScheduled) {
                updateScheduled = true;
                AndroidUtilities.runOnUIThread(() -> {
                    updateScheduled = false;
                    refreshQueue();
                }, 300);
            }
        });
    }
}
