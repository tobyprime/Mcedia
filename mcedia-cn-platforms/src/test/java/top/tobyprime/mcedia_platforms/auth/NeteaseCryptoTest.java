package top.tobyprime.mcedia_platforms.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NeteaseCryptoTest {

    @Test
    void aesEncryptProducesNonEmptyBase64() throws Exception {
        var result = NeteaseCrypto.aesEncrypt("{\"test\":\"data\"}", "0CoJUm6Qyw8W8jud");
        assertNotNull(result);
        assertFalse(result.isBlank());
        // Base64 valid
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(result));
    }

    @Test
    void aesEncryptDeterministicWithSameKey() throws Exception {
        var data = "{\"ids\":\"[123]\",\"br\":128000,\"csrf_token\":\"\"}";
        var r1 = NeteaseCrypto.aesEncrypt(data, "0CoJUm6Qyw8W8jud");
        var r2 = NeteaseCrypto.aesEncrypt(data, "0CoJUm6Qyw8W8jud");
        assertEquals(r1, r2);
    }

    @Test
    void rsaEncryptReturns256HexChars() {
        var result = NeteaseCrypto.rsaEncrypt("aaaabbbbccccdddd");
        assertNotNull(result);
        assertEquals(256, result.length());
        assertTrue(result.matches("[0-9a-f]+"));
    }

    @Test
    void rsaEncryptFixedKeyMatchesExpected() {
        var result = NeteaseCrypto.rsaEncrypt("aaaabbbbccccdddd");
        assertEquals("814e4abf9c1c6a2af74a7ecca8843f3052626c5c054584352e3fd38a519bd659e687cf1c079e1aac5dd9d491af6b8abf92109862ada93dc7b0ef94a8ee79d557ff2a20512b87ce507e357861366b8542139c67896748852d4086104a8dfc99a2e2e0640b46a4357407b72407b2849b323425c6ed45a0222e69d551a2e59e15b7", result);
    }

    @Test
    void encryptReturnsParamsAndEncSecKey() throws Exception {
        var rawJson = "{\"ids\":\"[123]\",\"br\":128000,\"csrf_token\":\"\"}";
        var result = NeteaseCrypto.encrypt(rawJson);

        assertTrue(result.containsKey("params"));
        assertTrue(result.containsKey("encSecKey"));
        assertFalse(result.get("params").isBlank());
        assertEquals(256, result.get("encSecKey").length());
    }

    @Test
    void encryptReturnsNonDeterministicEncSecKey() throws Exception {
        var rawJson = "{\"test\":\"data\",\"csrf_token\":\"\"}";
        var r1 = NeteaseCrypto.encrypt(rawJson);
        var r2 = NeteaseCrypto.encrypt(rawJson);

        assertEquals(256, r1.get("encSecKey").length());
        assertEquals(256, r2.get("encSecKey").length());
        // Dynamic key means encSecKey differs between calls
        assertNotEquals(r1.get("encSecKey"), r2.get("encSecKey"));
        // params should also differ
        assertNotEquals(r1.get("params"), r2.get("params"));
    }

    @Test
    void generateRandomKeyReturns16Chars() {
        var key = NeteaseCrypto.generateRandomKey();
        assertNotNull(key);
        assertEquals(16, key.length());
    }

    @Test
    void generateRandomKeyIsRandom() {
        var k1 = NeteaseCrypto.generateRandomKey();
        var k2 = NeteaseCrypto.generateRandomKey();
        // Extremely unlikely to collide
        assertNotEquals(k1, k2);
    }
}
