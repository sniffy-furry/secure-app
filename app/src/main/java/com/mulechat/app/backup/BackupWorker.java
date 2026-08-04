package com.mulechat.app.backup;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mulechat.app.data.NulChatRepository;
import com.mulechat.app.identity.Identity;
import com.mulechat.app.storage.AppDatabase;
import com.mulechat.app.util.IdentityStore;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Runs on WorkManager's schedule (see BackupScheduler) -- builds an
 * encrypted backup (see BackupExporter) and writes it as a new dated file
 * into whatever folder the user picked in Settings, then prunes older
 * backups beyond KEEP_COUNT. Returns success (not failure) if backups
 * aren't enabled, no folder has been chosen, or there's no identity yet --
 * those are all valid states, not errors. Returns retry() on anything that
 * looks transient (folder temporarily unwritable, I/O hiccup) so
 * WorkManager's own backoff handles it rather than us inventing retry
 * logic here.
 */
public final class BackupWorker extends Worker {

    private static final String TAG = "BackupWorker";
    private static final String FILE_PREFIX = "mulechat-backup-";
    private static final String FILE_SUFFIX = ".mcbackup";
    private static final int KEEP_COUNT = 5;

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        IdentityStore settings = new IdentityStore(context);

        if (!settings.isBackupEnabled()) return Result.success();
        String folderUriString = settings.getBackupFolderUri();
        if (folderUriString == null) return Result.success();

        try {
            NulChatRepository repo = AppDatabase.getOrOpen(context);
            Identity identity = repo.getIdentity();
            if (identity == null) return Result.success();

            byte[] encrypted = BackupExporter.buildEncryptedBackup(repo, identity);

            DocumentFile folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUriString));
            if (folder == null || !folder.canWrite()) {
                Log.w(TAG, "Backup folder is missing or no longer writable");
                return Result.retry();
            }

            String fileName = FILE_PREFIX
                    + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                    + FILE_SUFFIX;
            DocumentFile newFile = folder.createFile("application/octet-stream", fileName);
            if (newFile == null) return Result.retry();

            try (OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri())) {
                if (out == null) return Result.retry();
                out.write(encrypted);
            }

            prune(folder);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Backup failed", e);
            return Result.retry();
        }
    }

    private void prune(DocumentFile folder) {
        DocumentFile[] allFiles = folder.listFiles();
        List<DocumentFile> backups = new ArrayList<>();
        for (DocumentFile file : allFiles) {
            if (file.getName() != null && file.getName().startsWith(FILE_PREFIX)) backups.add(file);
        }
        // Dated filenames (yyyyMMdd-HHmmss) sort lexicographically in
        // chronological order, so a plain name sort is enough here.
        Collections.sort(backups, (a, b) -> a.getName().compareTo(b.getName()));
        while (backups.size() > KEEP_COUNT) {
            backups.remove(0).delete();
        }
    }
}
