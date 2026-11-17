package com.passwordshield;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 密码生成器与强度估算。
 * 使用 SecureRandom + 拒绝采样保证均匀分布，保证每类选中字符至少出现一次。
 */
public final class PwGen {
    public static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    public static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String DIGIT = "0123456789";
    public static final String SYMBOL = "!@#$%^&*()-_=+[]{};:,.<>?/~";
    private static final String AMBIGUOUS = "0O1lI";
    private static final SecureRandom RNG = new SecureRandom();

    private PwGen() {
    }

    public static String generate(int len, boolean lower, boolean upper, boolean digit,
                                  boolean symbol, boolean noAmbig) {
        List<String> groups = new ArrayList<>();
        addGroup(groups, LOWER, lower, noAmbig);
        addGroup(groups, UPPER, upper, noAmbig);
        addGroup(groups, DIGIT, digit, noAmbig);
        addGroup(groups, SYMBOL, symbol, noAmbig);
        if (groups.isEmpty() || len <= 0) return null;

        StringBuilder pool = new StringBuilder();
        for (String g : groups) {
            for (int i = 0; i < g.length(); i++) {
                char c = g.charAt(i);
                if (pool.indexOf(String.valueOf(c)) < 0) pool.append(c);
            }
        }

        char[] out = new char[len];
        int idx = 0;
        for (String g : groups) {
            if (idx >= len) break;
            out[idx++] = g.charAt(randInt(g.length()));
        }
        while (idx < len) {
            out[idx++] = pool.charAt(randInt(pool.length()));
        }
        // Fisher-Yates 洗牌（crypto 随机）
        for (int i = idx - 1; i > 0; i--) {
            int j = randInt(i + 1);
            char t = out[i];
            out[i] = out[j];
            out[j] = t;
        }
        return new String(out);
    }

    private static void addGroup(List<String> groups, String set, boolean on, boolean noAmbig) {
        if (!on) return;
        if (noAmbig) {
            StringBuilder b = new StringBuilder();
            for (char c : set.toCharArray()) {
                if (AMBIGUOUS.indexOf(c) < 0) b.append(c);
            }
            if (b.length() > 0) groups.add(b.toString());
        } else {
            groups.add(set);
        }
    }

    /** [0, bound) 均匀随机整数（拒绝采样，避免模偏差） */
    private static int randInt(int bound) {
        if (bound <= 1) return 0;
        long limit = (0xFFFFFFFFL / bound) * bound;
        long v;
        do {
            v = RNG.nextInt() & 0xFFFFFFFFL;
        } while (v >= limit);
        return (int) (v % bound);
    }

    /** 按密码内容估算字符池大小，返回香农熵位数 */
    public static int entropyBits(String pw) {
        if (pw == null || pw.isEmpty()) return 0;
        int kinds = 0;
        for (int i = 0; i < pw.length(); i++) {
            char c = pw.charAt(i);
            if (c >= 'a' && c <= 'z') kinds |= 1;
            else if (c >= 'A' && c <= 'Z') kinds |= 2;
            else if (c >= '0' && c <= '9') kinds |= 4;
            else kinds |= 8;
        }
        int size = 0;
        if ((kinds & 1) != 0) size += 26;
        if ((kinds & 2) != 0) size += 26;
        if ((kinds & 4) != 0) size += 10;
        if ((kinds & 8) != 0) size += 32;
        return (int) Math.round(pw.length() * (Math.log(Math.max(size, 1)) / Math.log(2)));
    }

    /** 0 空，1 弱(<40)，2 中(<60)，3 强(<80)，4 极强 */
    public static int strengthLevel(String pw) {
        if (pw == null || pw.isEmpty()) return 0;
        int bits = entropyBits(pw);
        if (bits < 40) return 1;
        if (bits < 60) return 2;
        if (bits < 80) return 3;
        return 4;
    }
}
