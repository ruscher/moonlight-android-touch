package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.limelight.BuildConfig;
import com.limelight.R;
import com.limelight.preferences.AddComputerManually;

/**
 * Big Remote Play Controller dialogs: how to connect, and About. Both are offline
 * (text only); nothing here touches the network.
 */
public final class BrpDialogs {
    private BrpDialogs() {}

    /** Steps to reach a Big Remote Play computer, with a shortcut to Add computer. */
    public static void showConnectHelp(final Activity activity) {
        if (activity.isFinishing()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.brp_help_title)
                .setMessage(R.string.brp_help_body)
                .setPositiveButton(R.string.brp_add_computer, (dialog, which) ->
                        activity.startActivity(new Intent(activity, AddComputerManually.class)))
                .setNegativeButton(R.string.brp_close, null);

        // The upstream guide is a web page; Fire TV reviewers found it hard to
        // navigate with a remote, so it is only offered elsewhere.
        if (!activity.getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
            builder.setNeutralButton(R.string.brp_help_moonlight_guide, (dialog, which) ->
                    HelpLauncher.launchSetupGuide(activity));
        }

        builder.show();
    }

    /** Version, origin (Moonlight fork) and license. */
    public static void showAbout(Activity activity) {
        if (activity.isFinishing()) {
            return;
        }

        String message = activity.getString(R.string.brp_about_version, versionName(activity))
                + "\n" + activity.getPackageName()
                + "\n\n" + activity.getString(R.string.brp_about_body);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.brp_about_title)
                .setIcon(R.drawable.brp_logo)
                .setMessage(message)
                .setPositiveButton(R.string.brp_close, null)
                .show();
    }

    public static String versionName(Activity activity) {
        try {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return BuildConfig.VERSION_NAME;
        }
    }
}
