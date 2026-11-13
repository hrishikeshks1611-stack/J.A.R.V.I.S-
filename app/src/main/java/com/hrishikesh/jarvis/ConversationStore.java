package com.hrishikesh.jarvis;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Fresh per-install conversation store. Message bodies are AES-GCM encrypted with a key
 * held by Android Keystore; no old app database or backup is imported.
 */
public final class ConversationStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "jarvis_memory.db";
    private static final int DB_VERSION = 1;
    private static final String KEY_ALIAS = "jarvis.fresh.memory.aes";
    private static final String TABLE = "messages";

    public static final class Entry {
        public final String role;
        public final String text;
        public final long createdAt;
        Entry(String role, String text, long createdAt) {
            this.role = role; this.text = text; this.createdAt = createdAt;
        }
    }

    public ConversationStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, iv BLOB NOT NULL, body BLOB NOT NULL, created_at INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public synchronized void add(String role, String text) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ContentValues values = new ContentValues();
            values.put("role", role);
            values.put("iv", cipher.getIV());
            values.put("body", encrypted);
            values.put("created_at", System.currentTimeMillis());
            getWritableDatabase().insert(TABLE, null, values);
        } catch (Exception ignored) { }
    }

    public synchronized List<Entry> recent(int limit) {
        ArrayList<Entry> entries = new ArrayList<>();
        if (limit < 1) return entries;
        try (Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{"role", "iv", "body", "created_at"},
                null, null, null, null, "_id DESC", String.valueOf(Math.min(limit, 100)))) {
            while (cursor.moveToNext()) {
                String role = cursor.getString(0);
                String text = decrypt(cursor.getBlob(1), cursor.getBlob(2));
                entries.add(new Entry(role, text, cursor.getLong(3)));
            }
        } catch (Exception ignored) { }
        Collections.reverse(entries);
        return entries;
    }

    public synchronized void clear() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    private String decrypt(byte[] iv, byte[] encrypted) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return "[message could not be decrypted]"; }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
