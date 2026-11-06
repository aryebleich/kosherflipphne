package com.kosherflip.guard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

/**
 * "Share KosherFlip with another phone": sends the installed KosherFlip apps as
 * APK files over Bluetooth, Nearby Share, email, etc. - no internet or computer
 * needed on the other phone. Only our own packages are served.
 */
public class ShareProvider extends ContentProvider {
    static final String AUTHORITY = "com.kosherflip.guard.share";

    static Intent shareIntent(Context ctx) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (String[] a : Companions.APPS) {
            if (PolicyEnforcer.isInstalled(ctx, a[0])) uris.add(Uri.parse("content://" + AUTHORITY + "/" + a[1]));
        }
        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("application/vnd.android.package-archive")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!uris.isEmpty()) {
            android.content.ClipData clip = android.content.ClipData.newRawUri("KosherFlip", uris.get(0));
            for (int i = 1; i < uris.size(); i++) clip.addItem(new android.content.ClipData.Item(uris.get(i)));
            send.setClipData(clip);
        }
        return Intent.createChooser(send, "Send KosherFlip (install KosherGuard first)");
    }

    private File apkFor(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        for (String[] a : Companions.APPS) {
            if (a[1].equals(name)) {
                try {
                    ApplicationInfo ai = getContext().getPackageManager().getApplicationInfo(a[0], 0);
                    return new File(ai.sourceDir);
                } catch (PackageManager.NameNotFoundException e) {
                    break;
                }
            }
        }
        throw new FileNotFoundException(String.valueOf(name));
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("read only");
        return ParcelFileDescriptor.open(apkFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String s, String[] a, String o) {
        try {
            File f = apkFor(uri);
            MatrixCursor c = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[] {uri.getLastPathSegment(), f.length()});
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
