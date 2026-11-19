package com.limelight.grid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Collections;
import java.util.Comparator;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, getLayoutIdForPreferences(prefs));
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return R.layout.pc_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                return lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
            }
        });
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    @SuppressWarnings("deprecation") // Context.getColor() needs API 23; minSdk is 21
    private int color(int id) {
        return context.getResources().getColor(id);
    }

    @Override
    public void populateView(View parentView, ImageView imgView, ProgressBar prgView, TextView txtView, ImageView overlayView, PcView.ComputerObject obj) {
        ComputerDetails details = obj.details;
        boolean online = details.state == ComputerDetails.State.ONLINE;
        boolean offline = details.state == ComputerDetails.State.OFFLINE;
        boolean checking = details.state == ComputerDetails.State.UNKNOWN;
        boolean paired = details.pairState == PairingManager.PairState.PAIRED;

        imgView.setImageResource(R.drawable.ic_computer);
        imgView.setAlpha(online || checking ? 1.0f : 0.6f);
        imgView.setImageTintList(ColorStateList.valueOf(color(
                offline ? R.color.brp_status_offline : R.color.brp_accent_text)));

        View chip = parentView.findViewById(R.id.grid_image_layout);
        if (chip != null) {
            chip.setBackgroundTintList(ColorStateList.valueOf(color(
                    offline ? R.color.brp_chip_neutral : R.color.brp_chip)));
        }

        prgView.setVisibility(checking ? View.VISIBLE : View.INVISIBLE);

        txtView.setText(details.name);
        txtView.setAlpha(offline ? 0.75f : 1.0f);

        if (offline) {
            overlayView.setImageResource(R.drawable.ic_pc_offline);
            overlayView.setVisibility(View.VISIBLE);
        }
        // We must check if the status is exactly online and unpaired
        // to avoid colliding with the loading spinner when status is unknown
        else if (online && details.pairState == PairingManager.PairState.NOT_PAIRED) {
            overlayView.setImageResource(R.drawable.ic_lock);
            overlayView.setVisibility(View.VISIBLE);
        }
        else {
            overlayView.setVisibility(View.GONE);
        }

        // State in words, never by colour alone: Online · Paired, Not paired, Playing, ...
        String status;
        int statusColor;
        if (offline) {
            status = context.getString(R.string.brp_status_offline);
            statusColor = R.color.brp_status_offline;
        }
        else if (checking) {
            status = context.getString(R.string.brp_status_checking);
            statusColor = R.color.brp_status_offline;
        }
        else if (!paired) {
            status = context.getString(R.string.brp_status_pair,
                    context.getString(R.string.brp_status_online),
                    context.getString(R.string.brp_status_not_paired));
            statusColor = R.color.brp_status_attention;
        }
        else if (details.runningGameId != 0) {
            status = context.getString(R.string.brp_status_pair,
                    context.getString(R.string.brp_status_online),
                    context.getString(R.string.brp_status_playing));
            statusColor = R.color.brp_status_playing;
        }
        else {
            status = context.getString(R.string.brp_status_pair,
                    context.getString(R.string.brp_status_online),
                    context.getString(R.string.brp_status_paired));
            statusColor = R.color.brp_status_online;
        }

        TextView statusView = parentView.findViewById(R.id.grid_status);
        if (statusView != null) {
            statusView.setText(status);
            statusView.setTextColor(color(statusColor));
        }
        View dot = parentView.findViewById(R.id.grid_status_dot);
        if (dot != null) {
            dot.setBackgroundTintList(ColorStateList.valueOf(color(statusColor)));
        }

        // Screen readers get the name and the state in one announcement
        parentView.setContentDescription(details.name + ", " + status);
    }
}
