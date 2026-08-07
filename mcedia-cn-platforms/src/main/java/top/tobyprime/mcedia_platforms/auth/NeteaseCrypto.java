package top.tobyprime.mcedia_platforms.auth;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NeteaseCrypto {
    private static final String AES_KEY = "0CoJUm6Qyw8W8jud";
    private static final String AES_IV = "0102030405060708";
    private static final String RSA_PUBLIC_EXPONENT = "010001";
    private static final String RSA_MODULUS = "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private NeteaseCrypto() {
    }

    static String aesEncrypt(String content, String key) throws Exception {
        var keyBytes = key.getBytes(StandardCharsets.UTF_8);
        var ivBytes = AES_IV.getBytes(StandardCharsets.UTF_8);
        var keySpec = new SecretKeySpec(keyBytes, "AES");
        var ivSpec = new IvParameterSpec(ivBytes);
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return Base64.getEncoder().encodeToString(cipher.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    static String rsaEncrypt(String text) {
        var reversed = new StringBuilder(text).reverse().toString();
        var hexBuilder = new StringBuilder();
        for (var b : reversed.getBytes(StandardCharsets.UTF_8)) {
            hexBuilder.append(String.format("%02x", b));
        }
        var textNum = new BigInteger(hexBuilder.toString(), 16);
        var modulus = new BigInteger(RSA_MODULUS, 16);
        var exponent = new BigInteger(RSA_PUBLIC_EXPONENT, 16);
        var encrypted = textNum.modPow(exponent, modulus);
        var encryptedHex = encrypted.toString(16);
        var padded = new StringBuilder();
        for (int i = 0; i < 256 - encryptedHex.length(); i++) {
            padded.append('0');
        }
        padded.append(encryptedHex);
        return padded.toString();
    }

    public static Map<String, String> encrypt(String rawJson) throws Exception {
        var firstEncrypted = aesEncrypt(rawJson, AES_KEY);
        var randomKey = generateRandomKey();
        var params = aesEncrypt(firstEncrypted, randomKey);
        var encSecKey = rsaEncrypt(randomKey);
        var map = new LinkedHashMap<String, String>();
        map.put("params", params);
        map.put("encSecKey", encSecKey);
        return map;
    }

    static String generateRandomKey() {
        var sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString();
    }
}
