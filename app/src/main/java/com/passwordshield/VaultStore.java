package com.passwordshield;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

/**
 * 密码库：内存中保存解密后的条目与密钥，落盘时整体重新加密为一条 AES-GCM 记录。
 * 文件格式（JSON）：{ v, iter, salt, iv, ct, updatedAt }，ct 为整库密文。
 */
public class VaultStore {
    public static class Entry {
        public String id;
        public String title = "";
        public String username = "";
        public String password = "";
        public String url = "";
        public String notes = "";
        public long createdAt;
        public long updatedAt;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("username", username);
            o.put("password", password);
            o.put("url", url);
            o.put("notes", notes);
            o.put("createdAt", createdAt);
            o.put("updatedAt", updatedAt);
            return o;
        }

        static Entry fromJson(JSONObject o) throws Exception {
            Entry e = new Entry();
            e.id = o.optString("id", UUID.randomUUID().toString());
            e.title = o.optString("title", "");
            e.username = o.optString("username", "");
            e.password = o.optString("password", "");
            e.url = o.optString("url", "");
            e.notes = o.optString("notes", "");
            e.createdAt = o.optLong("createdAt", 0);
            e.updatedAt = o.optLong("updatedAt", 0);
            return e;
        }
    }

    private static final String FILE_NAME = "vault.dat";
    private static VaultStore sInstance;

    private final File file;
    private SecretKey key;
    private byte[] salt;
    private int iterations = Crypto.ITERATIONS;
    public final List<Entry> entries = new ArrayList<>();

    private VaultStore(Context appContext) {
        file = new File(appContext.getFilesDir(), FILE_NAME);
    }

    public static synchronized VaultStore get(Context context) {
        if (sInstance == null) {
            sInstance = new VaultStore(context.getApplicationContext());
        }
        return sInstance;
    }

    public boolean exists() {
        return file.exists() && file.length() > 0;
    }

    public boolean isUnlocked() {
        return key != null;
    }

    /** 首次创建密码库 */
    public synchronized void create(char[] masterPw) throws Exception {
        salt = Crypto.randomBytes(Crypto.SALT_LEN);
        iterations = Crypto.ITERATIONS;
        key = Crypto.deriveKey(masterPw, salt, iterations);
        entries.clear();
        persist();
    }

    /** 用主密码解锁（密码错误时抛 GeneralSecurityException） */
    public synchronized void unlock(char[] masterPw) throws Exception {
        JSONObject rec = readRecord();
        byte[] s = Crypto.unb64(rec.getString("salt"));
        int it = rec.getInt("iter");
        SecretKey k = Crypto.deriveKey(masterPw, s, it);
        byte[] plain = Crypto.decrypt(k,
                Crypto.unb64(rec.getString("iv")),
                Crypto.unb64(rec.getString("ct")));
        salt = s;
        iterations = it;
        key = k;
        entries.clear();
        JSONArray arr = new JSONArray(new String(plain, StandardCharsets.UTF_8));
        for (int i = 0; i < arr.length(); i++) {
            try {
                entries.add(Entry.fromJson(arr.getJSONObject(i)));
            } catch (Exception ignored) {
            }
        }
    }

    /** 整库重新加密并写盘 */
    public synchronized void persist() throws Exception {
        if (key == null) throw new IllegalStateException("vault is locked");
        byte[] iv = Crypto.randomBytes(Crypto.IV_LEN);
        JSONArray arr = new JSONArray();
        for (Entry e : entries) arr.put(e.toJson());
        byte[] plain = arr.toString().getBytes(StandardCharsets.UTF_8);
        byte[] ct = Crypto.encrypt(key, plain, iv);
        JSONObject rec = new JSONObject();
        rec.put("v", 1);
        rec.put("iter", iterations);
        rec.put("salt", Crypto.b64(salt));
        rec.put("iv", Crypto.b64(iv));
        rec.put("ct", Crypto.b64(ct));
        rec.put("updatedAt", System.currentTimeMillis());
        writeFile(rec.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 校验旧主密码并更换新主密码（重新派生、重新加盐） */
    public synchronized void rekey(char[] currentPw, char[] nextPw) throws Exception {
        JSONObject rec = readRecord();
        SecretKey old = Crypto.deriveKey(currentPw,
                Crypto.unb64(rec.getString("salt")), rec.getInt("iter"));
        Crypto.decrypt(old,
                Crypto.unb64(rec.getString("iv")),
                Crypto.unb64(rec.getString("ct")));
        byte[] ns = Crypto.randomBytes(Crypto.SALT_LEN);
        SecretKey nk = Crypto.deriveKey(nextPw, ns, Crypto.ITERATIONS);
        salt = ns;
        iterations = Crypto.ITERATIONS;
        key = nk;
        persist();
    }

    /** 锁定：清除内存中的密钥与明文 */
    public synchronized void lock() {
        key = null;
        entries.clear();
    }

    /** 重置：删除密码库文件 */
    public synchronized void reset() {
        key = null;
        entries.clear();
        file.delete();
    }

    /** 导出用：返回加密记录原文 */
    public synchronized String recordJson() throws IOException {
        return new String(readFile(), StandardCharsets.UTF_8);
    }

    /** 导入用：覆盖加密记录并锁定（之后需用备份的主密码解锁） */
    public synchronized void replaceRecord(String json) throws IOException {
        try {
            JSONObject rec = new JSONObject(json);
            if (!rec.has("salt") || !rec.has("iv") || !rec.has("ct") || !rec.has("iter")) {
                throw new IOException("bad record");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("bad record");
        }
        lock();
        writeFile(json.getBytes(StandardCharsets.UTF_8));
    }

    private JSONObject readRecord() throws Exception {
        return new JSONObject(new String(readFile(), StandardCharsets.UTF_8));
    }

    private byte[] readFile() throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private void writeFile(byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(data);
            out.flush();
        } finally {
            out.close();
        }
    }
}
