package com.limelight.grid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.limelight.AppView;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unchecked")
public class AppGridAdapter extends GenericGridAdapter<AppView.AppObject> {
    private static final int ART_WIDTH_PX = 300;
    private static final int SMALL_WIDTH_DP = 100;
    private static final int LARGE_WIDTH_DP = 150;

    private final ComputerDetails computer;
    private final String uniqueId;
    private final boolean showHiddenApps;

    private CachedAppAssetLoader loader;
    private Set<Integer> hiddenAppIds = new HashSet<>();
    private ArrayList<AppView.AppObject> allApps = new ArrayList<>();

    public AppGridAdapter(Context context, PreferenceConfiguration prefs, ComputerDetails computer, String uniqueId, boolean showHiddenApps) {
        super(context, getLayoutIdForPreferences(prefs));

        this.computer = computer;
        this.uniqueId = uniqueId;
        this.showHiddenApps = showHiddenApps;

        updateLayoutWithPreferences(context, prefs);
    }

    public void updateHiddenApps(Set<Integer> newHiddenAppIds, boolean hideImmediately) {
        this.hiddenAppIds.clear();
        this.hiddenAppIds.addAll(newHiddenAppIds);

        if (hideImmediately) {
            // Reconstruct the itemList with the new hidden app set
            itemList.clear();
            for (AppView.AppObject app : allApps) {
                app.isHidden = hiddenAppIds.contains(app.app.getAppId());

                if (!app.isHidden || showHiddenApps) {
                    itemList.add(app);
                }
            }
        }
        else {
            // Just update the isHidden state to show the correct UI indication
            for (AppView.AppObject app : allApps) {
                app.isHidden = hiddenAppIds.contains(app.app.getAppId());
            }
        }

        notifyDataSetChanged();
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        if (prefs.smallIconMode) {
            return R.layout.app_grid_item_small;
        }
        else {
            return R.layout.app_grid_item;
        }
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        int dpi = context.getResources().getDisplayMetrics().densityDpi;
        int dp;

        if (prefs.smallIconMode) {
            dp = SMALL_WIDTH_DP;
        }
        else {
            dp = LARGE_WIDTH_DP;
        }

        double scalingDivisor = ART_WIDTH_PX / (dp * (dpi / 160.0));
        if (scalingDivisor < 1.0) {
            // We don't want to make them bigger before draw-time
            scalingDivisor = 1.0;
        }
        LimeLog.info("Art scaling divisor: " + scalingDivisor);

        if (loader != null) {
            // Cancel operations on the old loader
            cancelQueuedOperations();
        }

        this.loader = new CachedAppAssetLoader(computer, scalingDivisor,
                new NetworkAssetLoader(context, uniqueId),
                new MemoryAssetLoader(),
                new DiskAssetLoader(context),
                BitmapFactory.decodeResource(context.getResources(), R.drawable.no_app_image));

        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    public void cancelQueuedOperations() {
        loader.cancelForegroundLoads();
        loader.cancelBackgroundLoads();
        loader.freeCacheMemory();
    }

    private static void sortList(List<AppView.AppObject> list) {
        Collections.sort(list, new Comparator<AppView.AppObject>() {
            @Override
            public int compare(AppView.AppObject lhs, AppView.AppObject rhs) {
                return lhs.app.getAppName().toLowerCase().compareTo(rhs.app.getAppName().toLowerCase());
            }
        });
    }

    public void addApp(AppView.AppObject app) {
        // Update hidden state
        app.isHidden = hiddenAppIds.contains(app.app.getAppId());

        // Always add the app to the all apps list
        allApps.add(app);
        sortList(allApps);

        // Add the app to the adapter data if it's not hidden
        if (showHiddenApps || !app.isHidden) {
            // Queue a request to fetch this bitmap into cache
            loader.queueCacheLoad(app.app);

            // Add the app to our sorted list
            itemList.add(app);
            sortList(itemList);
        }
    }

    public void removeApp(AppView.AppObject app) {
        itemList.remove(app);
        allApps.remove(app);
    }

    @Override
    public void clear() {
        super.clear();
        allApps.clear();
    }

    @SuppressWarnings("deprecation") // Context.getColor() needs API 23; minSdk is 21
    private int color(int id) {
        return context.getResources().getColor(id);
    }

    private static int badgeIcon(BrpAppKind.Kind kind) {
        switch (kind) {
            case DESKTOP:
                return R.drawable.ic_brp_desktop;
            case MONITOR:
                return R.drawable.ic_brp_monitor;
            case STEAM:
                return R.drawable.ic_brp_steam;
            default:
                return R.drawable.ic_brp_game;
        }
    }

    @Override
    public void populateView(View parentView, ImageView imgView, ProgressBar prgView, TextView txtView, ImageView overlayView, AppView.AppObject obj) {
        // Round the box art with the card (the XML attribute only works on API 31+)
        parentView.setClipToOutline(true);

        // Recognisable icon behind the box art; it shows when the computer sends none
        BrpAppKind kind = BrpAppKind.of(obj.app.getAppName());
        ImageView badge = parentView.findViewById(R.id.grid_badge);
        if (badge != null) {
            badge.setImageResource(badgeIcon(kind.kind));
        }
        TextView badgeText = parentView.findViewById(R.id.grid_badge_text);
        if (badgeText != null) {
            if (kind.number != null) {
                badgeText.setText(kind.number);
                badgeText.setVisibility(View.VISIBLE);
            }
            else {
                badgeText.setVisibility(View.GONE);
            }
        }

        // Let the cached asset loader handle it
        loader.populateImageView(obj.app, imgView, txtView);

        if (obj.isRunning) {
            // Show the play button overlay
            overlayView.setImageResource(R.drawable.ic_play);
            overlayView.setVisibility(View.VISIBLE);
        }
        else {
            overlayView.setVisibility(View.GONE);
        }

        if (obj.isHidden) {
            parentView.setAlpha(0.40f);
        }
        else {
            parentView.setAlpha(1.0f);
        }

        // State in words: Playing, Hidden, Low bandwidth
        TextView pill = parentView.findViewById(R.id.grid_pill);
        String pillText = null;
        if (pill != null) {
            // Solid backgrounds: the pill must stay legible over box art
            int background = R.color.brp_window;
            int foreground = R.color.brp_text;
            if (obj.isRunning) {
                pillText = context.getString(R.string.brp_status_playing);
                background = R.color.brp_primary;
                foreground = R.color.brp_on_primary;
            }
            else if (obj.isHidden) {
                pillText = context.getString(R.string.brp_app_hidden);
            }
            else if (kind.lowBandwidth) {
                pillText = context.getString(R.string.brp_app_low_bandwidth);
                foreground = R.color.brp_accent_text;
            }

            if (pillText != null) {
                pill.setText(pillText);
                pill.setTextColor(color(foreground));
                pill.setBackgroundTintList(ColorStateList.valueOf(color(background)));
                pill.setVisibility(View.VISIBLE);
            }
            else {
                pill.setVisibility(View.GONE);
            }
        }

        // The name is always spoken, even when only the box art is visible
        String name = obj.app.getAppName();
        parentView.setContentDescription(pillText != null ? name + ", " + pillText : name);
    }
}
