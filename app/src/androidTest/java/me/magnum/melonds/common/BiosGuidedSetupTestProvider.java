package me.magnum.melonds.common;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test APK process intentionally depends only on Android/Java, not the target's Kotlin runtime. */
public final class BiosGuidedSetupTestProvider extends DocumentsProvider {
    /** Runs under the provider APK's own UID, which can issue its normal URI grant. */
    public static final class GrantReceiver extends BroadcastReceiver {
        public static final String GRANT = "me.magnum.melonds.test.BIOS_FIXTURE_GRANT";
        public static final String REVOKE = "me.magnum.melonds.test.BIOS_FIXTURE_REVOKE";

        @Override public void onReceive(Context context, Intent intent) {
            if (!isOrderedBroadcast()) return;
            try {
                String testPackage = context.getPackageName();
                if (!testPackage.endsWith(".test")) throw new IllegalStateException("Expected test APK package");
                // No caller-supplied package or URI: this can only expose this test authority
                // to the corresponding instrumentation target.
                String targetPackage = testPackage.substring(0, testPackage.length() - ".test".length());
                String authority = context.getPackageManager().getProviderInfo(
                        new ComponentName(context, BiosGuidedSetupTestProvider.class), 0).authority;
                Uri root = new Uri.Builder().scheme("content").authority(authority).build();
                int modes = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                if (GRANT.equals(intent.getAction())) {
                    context.grantUriPermission(targetPackage, root, modes | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                } else if (REVOKE.equals(intent.getAction())) {
                    if (Build.VERSION.SDK_INT >= 26) context.revokeUriPermission(targetPackage, root, modes);
                    else context.revokeUriPermission(root, modes);
                } else throw new IllegalArgumentException("Unknown fixture grant action");
                setResultCode(Activity.RESULT_OK);
                setResultData(targetPackage);
            } catch (PackageManager.NameNotFoundException | RuntimeException error) {
                setResultCode(Activity.RESULT_CANCELED);
                setResultData(error.toString());
            }
        }
    }

    private static final String[] COLUMNS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS};

    private static final class Node {
        final String id = UUID.randomUUID().toString();
        final String parent;
        final Fixture fixture;
        final AtomicInteger readOpens = new AtomicInteger();
        volatile File file;
        Node(Fixture fixture, String parent, File file) {
            this.fixture = fixture; this.parent = parent; this.file = file;
        }
    }

    private static final class Fixture {
        final File directory;
        final byte[] sourceBytes;
        final ConcurrentHashMap<String, Node> nodes = new ConcurrentHashMap<>();
        final AtomicInteger activeDescriptors = new AtomicInteger();
        final AtomicInteger renameCalls = new AtomicInteger();
        final AtomicInteger published = new AtomicInteger();
        final AtomicInteger canonicalCreates = new AtomicInteger();
        final AtomicInteger publishBeforeClose = new AtomicInteger();
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch pipeFinished = new CountDownLatch(1);
        volatile boolean pipeStarted;
        volatile String mode;
        Node root;
        Fixture(File directory, byte[] sourceBytes, String mode) {
            this.directory = directory; this.sourceBytes = sourceBytes; this.mode = mode;
        }
    }

    private final ConcurrentHashMap<String, Fixture> fixtures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Node> nodes = new ConcurrentHashMap<>();
    private final Handler closeHandler = new Handler(Looper.getMainLooper());

    @Override public boolean onCreate() { return true; }

    @Override public Cursor queryRoots(String[] projection) {
        String[] columns = projection != null ? projection : new String[] {
                Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS};
        // Fixtures are reached only through their returned tree URI, not the user's picker.
        return new MatrixCursor(columns);
    }

    @Override public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : COLUMNS);
        addRow(cursor, node(id));
        return cursor;
    }

    @Override public Cursor queryChildDocuments(String id, String[] projection, String sortOrder)
            throws FileNotFoundException {
        Node parent = node(id);
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : COLUMNS);
        for (Node child : parent.fixture.nodes.values()) {
            if (id.equals(child.parent)) addRow(cursor, child);
        }
        return cursor;
    }

    @Override public boolean isChildDocument(String parentId, String childId) {
        Node child = nodes.get(childId);
        while (child != null && child.parent != null) {
            if (parentId.equals(child.parent)) return true;
            child = nodes.get(child.parent);
        }
        return false;
    }

    private void addRow(MatrixCursor cursor, Node node) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            switch (column) {
                case Document.COLUMN_DOCUMENT_ID: row.add(node.id); break;
                case Document.COLUMN_DISPLAY_NAME: row.add(node.file.getName()); break;
                case Document.COLUMN_MIME_TYPE:
                    row.add(node.file.isDirectory() ? Document.MIME_TYPE_DIR : "application/octet-stream"); break;
                case Document.COLUMN_SIZE: row.add(node.file.length()); break;
                case Document.COLUMN_FLAGS:
                    row.add(node.file.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE :
                            Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_RENAME | Document.FLAG_SUPPORTS_DELETE); break;
                default: row.add(null);
            }
        }
    }

    @Override public String createDocument(String parentId, String mimeType, String displayName)
            throws FileNotFoundException {
        Node parent = node(parentId);
        if (displayName.contains("/") || displayName.equals(".") || displayName.equals("..")) {
            throw new FileNotFoundException("Invalid fixture name");
        }
        File file = new File(parent.file, displayName);
        try {
            boolean created = Document.MIME_TYPE_DIR.equals(mimeType) ? file.mkdir() : file.createNewFile();
            if (!created) throw new IOException("Fixture document already exists");
            Node child = add(parent.fixture, parentId, file);
            if (!file.isDirectory() && !displayName.endsWith(".part")) parent.fixture.canonicalCreates.incrementAndGet();
            return child.id;
        } catch (IOException error) { throw missing(error); }
    }

    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        Node node = node(id);
        Fixture fixture = node.fixture;
        boolean write = mode.contains("w");
        if (write && fixture.mode.equals("open-delete-refusal")) {
            throw new FileNotFoundException("Injected output-open failure");
        }
        if (!write && node.file.getName().equals("nand.bin") &&
                fixture.mode.equals("blocked-nand") && node.readOpens.incrementAndGet() == 2) {
            return blockedNand(fixture);
        }
        fixture.activeDescriptors.incrementAndGet();
        try {
            return ParcelFileDescriptor.open(node.file, ParcelFileDescriptor.parseMode(mode), closeHandler, error -> {
                fixture.activeDescriptors.decrementAndGet();
                synchronized (fixture) { fixture.notifyAll(); }
            });
        } catch (IOException error) {
            fixture.activeDescriptors.decrementAndGet();
            throw missing(error);
        }
    }

    private ParcelFileDescriptor blockedNand(Fixture fixture) throws FileNotFoundException {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
            fixture.pipeStarted = true;
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(fixture.sourceBytes, 0, 4096);
                    if (fixture.release.await(10000, TimeUnit.MILLISECONDS)) {
                        output.write(fixture.sourceBytes, 4096, fixture.sourceBytes.length - 4096);
                    }
                } catch (IOException ignored) {
                    // Cancellation can close the reader before the released tail is consumed.
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                } finally { fixture.pipeFinished.countDown(); }
            }, "bios-fixture-nand").start();
            return pipe[0];
        } catch (IOException error) { throw missing(error); }
    }

    @Override public String renameDocument(String id, String displayName) throws FileNotFoundException {
        Node node = node(id);
        Fixture fixture = node.fixture;
        fixture.renameCalls.incrementAndGet();
        // Close callbacks are asynchronous across Binder. A bounded wait avoids a callback race;
        // a same-thread rename before closing its streams cannot satisfy this barrier.
        long deadline = SystemClock.elapsedRealtime() + 3000;
        synchronized (fixture) {
            while (fixture.activeDescriptors.get() != 0 && SystemClock.elapsedRealtime() < deadline) {
                try { fixture.wait(Math.max(1, deadline - SystemClock.elapsedRealtime())); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw missing(error); }
            }
        }
        if (fixture.activeDescriptors.get() != 0) {
            fixture.publishBeforeClose.incrementAndGet();
            throw new FileNotFoundException("Rename attempted before source/output close");
        }
        if (fixture.mode.equals("rename-refusal")) throw new FileNotFoundException("Injected rename refusal");
        File target = new File(node.file.getParentFile(), displayName);
        if (target.exists() || !node.file.renameTo(target)) throw new FileNotFoundException("Cannot publish fixture");
        node.file = target;
        fixture.published.incrementAndGet();
        return null; // Stable document ID after rename, as required by DocumentsProvider.
    }

    @Override public void deleteDocument(String id) throws FileNotFoundException {
        Node node = node(id);
        if (node.fixture.mode.equals("open-delete-refusal")) throw new FileNotFoundException("Injected delete refusal");
        if (!node.file.delete()) throw new FileNotFoundException("Cannot remove fixture document");
        node.fixture.nodes.remove(id);
        nodes.remove(id);
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle inherited = super.call(method, arg, extras);
        if (inherited != null || !method.startsWith("fixture-")) return inherited;
        Bundle result = new Bundle();
        try {
            if (method.equals("fixture-register")) {
                byte[] bytes = Objects.requireNonNull(Objects.requireNonNull(extras).getByteArray("bytes"));
                String name = Objects.requireNonNull(extras.getString("name"));
                File directory = new File(Objects.requireNonNull(getContext()).getCacheDir(), "bios-saf-" + UUID.randomUUID());
                if (!directory.mkdir()) throw new IOException("Cannot create fixture root");
                Fixture fixture = new Fixture(directory, bytes, arg);
                fixture.root = add(fixture, null, directory);
                File source = new File(directory, name);
                try (FileOutputStream output = new FileOutputStream(source)) { output.write(bytes); }
                add(fixture, fixture.root.id, source);
                fixtures.put(fixture.root.id, fixture);
                result.putString("id", fixture.root.id);
                result.putInt("pid", Process.myPid());
                return result;
            }
            Fixture fixture = Objects.requireNonNull(fixtures.get(arg), "Unknown fixture root");
            switch (method) {
                case "fixture-status":
                    result.putInt("renames", fixture.renameCalls.get());
                    result.putInt("published", fixture.published.get());
                    result.putInt("canonicalCreates", fixture.canonicalCreates.get());
                    result.putInt("publishBeforeClose", fixture.publishBeforeClose.get());
                    result.putInt("activeDescriptors", fixture.activeDescriptors.get());
                    result.putBoolean("pipeStarted", fixture.pipeStarted);
                    long partialBytes = 0;
                    for (Node node : fixture.nodes.values()) {
                        if (node.file.getName().endsWith(".part")) partialBytes = Math.max(partialBytes, node.file.length());
                    }
                    result.putLong("partialBytes", partialBytes);
                    break;
                case "fixture-normal": fixture.mode = "normal"; break;
                case "fixture-release": fixture.release.countDown(); break;
                case "fixture-remove":
                    fixture.release.countDown();
                    if (fixture.pipeStarted && !fixture.pipeFinished.await(3000, TimeUnit.MILLISECONDS)) {
                        throw new IOException("NAND writer still owns fixture");
                    }
                    for (Node node : fixture.nodes.values()) nodes.remove(node.id);
                    deleteTree(fixture.directory);
                    fixtures.remove(arg);
                    break;
                default: throw new IllegalArgumentException("Unknown fixture control");
            }
            return result;
        } catch (IOException | InterruptedException error) { throw new IllegalStateException(error); }
    }

    private Node add(Fixture fixture, String parent, File file) {
        Node node = new Node(fixture, parent, file);
        fixture.nodes.put(node.id, node);
        nodes.put(node.id, node);
        return node;
    }

    private Node node(String id) throws FileNotFoundException {
        Node node = nodes.get(id);
        if (node == null) throw new FileNotFoundException("Unknown fixture document");
        return node;
    }

    private static void deleteTree(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new IOException("Cannot remove fixture " + file.getName());
    }

    private static FileNotFoundException missing(Exception cause) {
        FileNotFoundException error = new FileNotFoundException(cause.getMessage());
        error.initCause(cause);
        return error;
    }
}
