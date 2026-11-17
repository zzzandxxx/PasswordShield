package com.passwordshield;

import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加密核心：主密码 --PBKDF2-HMAC-SHA256(25万次)--> 256位密钥 --AES-256-GCM--> 密文库。
 * 仅使用 Android 平台内置 JCE 实现，无第三方依赖。
 */
public final class Crypto {
    public static final int ITERATIONS = 250000;
    public static final int SALT_LEN = 16;
    public static final int IV_LEN = 12;
    private static final SecureRandom RNG = new SecureRandom();

    private Crypto() {
    }

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    public static SecretKey deriveKey(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    public static byte[] encrypt(SecretKey key, byte[] plain, byte[] iv)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return c.doFinal(plain);
    }

    /** 主密码错误时 GCM 校验失败抛出 AEADBadTagException（GeneralSecurityException 子类） */
    public static byte[] decrypt(SecretKey key, byte[] iv, byte[] ct)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return c.doFinal(ct);
    }

    public static String b64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static byte[] unb64(String s) {
        return Base64.decode(s, Base64.DEFAULT);
    }
}
