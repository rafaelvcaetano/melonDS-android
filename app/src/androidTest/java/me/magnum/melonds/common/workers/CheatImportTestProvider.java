package me.magnum.melonds.common.workers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.DocumentsContract.Document;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Test APK's remote process has only Android/Java classes, not target-app dependencies. */
public final class CheatImportTestProvider extends ContentProvider {
    private static final class Entry {
        final String mode;
        final byte[] bytes;
        final long openTimeoutMillis;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch cancelled = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger activeOpens = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final AtomicBoolean returnedAfterCancel = new AtomicBoolean();
        volatile File file;
        volatile ParcelFileDescriptor withheldWriter;

        Entry(String mode, byte[] bytes, long openTimeoutMillis) {
            this.mode = mode;
            this.bytes = bytes;
            this.openTimeoutMillis = Math.max(1, Math.min(30000, openTimeoutMillis));
        }
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/xml"; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        final Entry entry;
        try {
            entry = entry(uri);
        } catch (FileNotFoundException error) {
            throw new IllegalArgumentException(error);
        }
        String[] columns = projection != null ? projection : new String[] {
                Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS};
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            switch (columns[i]) {
                case Document.COLUMN_DOCUMENT_ID: values[i] = uri.getLastPathSegment(); break;
                case Document.COLUMN_DISPLAY_NAME: values[i] = "fixture.xml"; break;
                case Document.COLUMN_MIME_TYPE: values[i] = "application/xml"; break;
                case Document.COLUMN_SIZE:
                    values[i] = entry.mode.equals("pipe") ? null : (long) entry.bytes.length;
                    break;
                case Document.COLUMN_FLAGS: values[i] = 0; break;
                default: values[i] = null;
            }
        }
        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(values);
        return cursor;
    }

    @Override public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter,
            Bundle opts) throws FileNotFoundException {
        return openAssetFile(uri, "r", null);
    }

    // ContentResolver's read-only AFD path uses the typed method with */*.
    @Override public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter,
            Bundle opts, CancellationSignal signal) throws FileNotFoundException {
        return openAssetFile(uri, "r", signal);
    }

    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode)
            throws FileNotFoundException {
        return openAssetFile(uri, mode, null);
    }

    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode,
            CancellationSignal signal) throws FileNotFoundException {
        if (!mode.equals("r")) throw new IllegalStateException("Read only");
        Entry entry = entry(uri);
        entry.opens.incrementAndGet();
        entry.activeOpens.incrementAndGet();
        if (signal != null) {
            signal.setOnCancelListener(() -> {
                entry.cancelled.countDown();
                if (entry.mode.equals("cancel")) entry.release.countDown();
            });
        }
        entry.entered.countDown();
        try {
            if (entry.mode.equals("cancel") || entry.mode.equals("late")) {
                if (!entry.release.await(entry.openTimeoutMillis, TimeUnit.MILLISECONDS)) {
                    throw new FileNotFoundException("Fixture open was not released");
                }
                if (entry.mode.equals("cancel")) {
                    if (signal != null && signal.isCanceled()) throw new OperationCanceledException();
                    throw new FileNotFoundException("Fixture cleanup released uncancelled open");
                }
            } else if (entry.mode.equals("failure")) {
                throw new FileNotFoundException("Injected provider failure");
            }
            if (entry.mode.equals("offset-pipe")) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                entry.withheldWriter = pipe[1];
                // Withhold the offset pipe's writer; the test observes the actual blocking phase.
                return new AssetFileDescriptor(pipe[0], 16, entry.bytes.length);
            }
            if (entry.mode.equals("pipe")) {
                // Small XML fits in the pipe. No producer thread survives the open.
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                        new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(entry.bytes);
                } catch (IOException error) {
                    pipe[0].close();
                    throw error;
                }
                return new AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH);
            }
            byte[] prefix = entry.mode.equals("slice")
                    ? "not XML: prefix".getBytes(StandardCharsets.UTF_8) : new byte[0];
            byte[] suffix = entry.mode.equals("slice")
                    ? "not XML: suffix".getBytes(StandardCharsets.UTF_8) : new byte[0];
            File file = File.createTempFile("cheat-provider-", ".xml",
                    Objects.requireNonNull(getContext()).getCacheDir());
            entry.file = file;
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(prefix);
                output.write(entry.bytes);
                output.write(suffix);
            }
            ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_ONLY, new Handler(Looper.getMainLooper()), error -> {
                        entry.closes.incrementAndGet();
                        entry.closed.countDown();
                        file.delete();
                    });
            entry.returnedAfterCancel.set(signal != null && signal.isCanceled());
            return new AssetFileDescriptor(descriptor, prefix.length, entry.bytes.length);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw asFileNotFound(error);
        } catch (IOException error) {
            if (error instanceof FileNotFoundException) throw (FileNotFoundException) error;
            throw asFileNotFound(error);
        } finally {
            entry.activeOpens.decrementAndGet();
        }
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if (method.equals("register")) {
            String id = UUID.randomUUID().toString();
            entries.put(id, new Entry(Objects.requireNonNull(arg),
                    Objects.requireNonNull(Objects.requireNonNull(extras).getByteArray("bytes")),
                    extras.getLong("open_timeout_ms", 10000)));
            result.putString("id", id);
            result.putInt("pid", Process.myPid());
            return result;
        }
        Entry entry = entries.get(Objects.requireNonNull(arg));
        if (entry == null) throw new IllegalStateException("Unknown fixture");
        long timeout = extras != null ? extras.getLong("timeout", 2000) : 2000;
        try {
            switch (method) {
                case "await-open":
                    result.putBoolean("ready", entry.entered.await(timeout, TimeUnit.MILLISECONDS));
                    break;
                case "await-cancel":
                    result.putBoolean("ready", entry.cancelled.await(timeout, TimeUnit.MILLISECONDS));
                    break;
                case "await-close":
                    result.putBoolean("ready", entry.closed.await(timeout, TimeUnit.MILLISECONDS));
                    break;
                case "release": release(entry); break;
                case "status":
                    result.putInt("opens", entry.opens.get());
                    result.putInt("active", entry.activeOpens.get());
                    result.putInt("closes", entry.closes.get());
                    result.putBoolean("returnedAfterCancel", entry.returnedAfterCancel.get());
                    break;
                case "remove":
                    release(entry);
                    if (entry.activeOpens.get() != 0) {
                        throw new IllegalStateException("An open still owns this fixture");
                    }
                    if (entry.file != null) entry.file.delete();
                    entries.remove(arg);
                    break;
                default: throw new IllegalStateException("Unknown fixture control: " + method);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
        return result;
    }

    private static void release(Entry entry) {
        entry.release.countDown();
        if (entry.withheldWriter != null) {
            try {
                entry.withheldWriter.close();
            } catch (IOException error) {
                throw new IllegalStateException(error);
            }
            entry.withheldWriter = null;
        }
    }

    private Entry entry(Uri uri) throws FileNotFoundException {
        Entry entry = entries.get(Objects.requireNonNull(uri.getLastPathSegment()));
        if (entry == null) throw new FileNotFoundException("Unknown fixture");
        return entry;
    }

    private static FileNotFoundException asFileNotFound(Exception cause) {
        FileNotFoundException error = new FileNotFoundException(cause.getMessage());
        error.initCause(cause);
        return error;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new IllegalStateException("Read only");
    }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
}
