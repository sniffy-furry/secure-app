package com.mulechat.app.backup;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Enqueues (or cancels) BackupWorker's periodic schedule. Call
 * rescheduleFromSettings() any time backup-related settings change --
 * SettingsFragment does, on the enable switch and right after a folder is
 * picked. Cheap and idempotent: WorkManager just replaces the existing
 * unique work request.
 */
public final class BackupScheduler {

    private static final String WORK_NAME = "mulechat-periodic-backup";

    private BackupScheduler() {}

    public static void rescheduleFromSettings(Context context, boolean enabled) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME);
            return;
        }

        // Not tied to network -- a SAF folder can be a plain local/USB
        // directory, and if it's actually backed by a sync app (Drive,
        // Dropbox, Syncthing...), that app handles its own upload timing;
        // we only ever write a local file.
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(BackupWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build();

        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }
}
