package com.star4droid.star2d.git;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The signed-in GitHub account: the token is encrypted with an Android Keystore key (falls back to
 * app-private storage on devices where the keystore misbehaves), the profile is kept for display.
 */
public final class GitHubSession {
    private GitHubSession() {}

    private static final String PREFS = "s2_github", ALIAS = "star2d_github_token";
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public interface Done<T> {
        void on(T result, GitException error);
    }

    public interface Job<T> {
        T run() throws Exception;
    }

    /** Runs {@code job} on a worker thread and reports on the UI thread. */
    public static <T> void async(final Job<T> job, final Done<T> done) {
        POOL.execute(new Runnable() {
            public void run() {
                T r = null;
                GitException err = null;
                try {
                    r = job.run();
                } catch (GitException e) {
                    err = e;
                } catch (Throwable e) {
                    err = new GitException(GitException.Kind.OTHER, e.getMessage() == null ? e.toString() : e.getMessage(), e);
                }
                final T fr = r;
                final GitException fe = err;
                UI.post(new Runnable() {
                    public void run() {
                        if (done != null) done.on(fr, fe);
                    }
                });
            }
        });
    }

    public static void background(Runnable r) {
        POOL.execute(r);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ token

    public static String token(Context c) {
        String stored = prefs(c).getString("token", null);
        if (stored == null) return null;
        try {
            if (stored.startsWith("k1:")) return decrypt(stored.substring(3));
            if (stored.startsWith("p1:")) return new String(Base64.decode(stored.substring(3), Base64.NO_WRAP), "UTF-8");
        } catch (Throwable t) {
            // key lost (restore on a new device): the user simply connects again
            signOut(c);
        }
        return null;
    }

    public static boolean connected(Context c) {
        String t = token(c);
        return t != null && !t.isEmpty();
    }

    public static GitHubApi api(Context c) {
        return new GitHubApi(token(c));
    }

    public static void save(Context c, String token, GitHubApi.User user) {
        String stored;
        try {
            stored = "k1:" + encrypt(token);
        } catch (Throwable t) {
            try {
                stored = "p1:" + Base64.encodeToString(token.getBytes("UTF-8"), Base64.NO_WRAP);
            } catch (Exception e) {
                return;
            }
        }
        SharedPreferences.Editor e = prefs(c).edit().putString("token", stored);
        if (user != null) e.putString("login", user.login).putString("name", user.name).putLong("id", user.id).putString("avatar", user.avatarUrl);
        e.apply();
    }

    public static GitHubApi.User user(Context c) {
        SharedPreferences p = prefs(c);
        String login = p.getString("login", null);
        if (login == null) return null;
        GitHubApi.User u = new GitHubApi.User();
        u.login = login;
        u.name = p.getString("name", login);
        u.id = p.getLong("id", 0);
        u.avatarUrl = p.getString("avatar", "");
        return u;
    }

    public static void signOut(Context c) {
        prefs(c).edit().clear().apply();
    }

    // ------------------------------------------------------------------ keystore

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            return g.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    private static String encrypt(String text) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] enc = c.doFinal(text.getBytes("UTF-8"));
        byte[] all = new byte[iv.length + enc.length + 1];
        all[0] = (byte) iv.length;
        System.arraycopy(iv, 0, all, 1, iv.length);
        System.arraycopy(enc, 0, all, 1 + iv.length, enc.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }

    private static String decrypt(String b64) throws Exception {
        byte[] all = Base64.decode(b64, Base64.NO_WRAP);
        int ivLen = all[0] & 0xff;
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 1, ivLen));
        return new String(c.doFinal(all, 1 + ivLen, all.length - 1 - ivLen), "UTF-8");
    }
}
