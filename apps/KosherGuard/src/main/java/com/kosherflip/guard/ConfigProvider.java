package com.kosherflip.guard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Hands the browser and the AI app their settings (also without device owner,
 * where managed configuration isn't available). Each app only gets its own
 * settings: the caller's package is checked.
 *   ContentResolver.call(content://com.kosherflip.guard.config, "config", null, null)
 */
public class ConfigProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"config".equals(method) || getContext() == null) return null;
        String caller = getCallingPackage();
        Policy p = Policy.load(getContext());
        if (BuildConfigShim.AI.equals(caller)) return PolicyEnforcer.aiConfig(p);
        if (BuildConfigShim.BROWSER.equals(caller)) return PolicyEnforcer.browserConfig(getContext(), p);
        return null;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
