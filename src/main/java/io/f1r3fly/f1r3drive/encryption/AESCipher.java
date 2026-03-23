package io.f1r3fly.f1r3drive.encryption;

import io.f1r3fly.f1r3drive.errors.F1r3DriveError;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class AESCipher {

    private static final String CIPHER_NAME = "AES/ECB/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";

    private final Cipher cipher;
    private final SecretKeySpec keySpec;

    private static AESCipher instance;

    private AESCipher(String keyToPath) throws F1r3DriveError {
        try {
            this.cipher = Cipher.getInstance(CIPHER_NAME);
            this.keySpec = readOrGenerateKey(keyToPath);
            this.cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IOException | InvalidKeyException e) {
            throw new F1r3DriveError("Failed to initialize AES cipher", e);
        }
    }

    public byte[] encrypt(byte[] data) throws F1r3DriveError {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return cipher.doFinal(data);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new F1r3DriveError("Failed to encrypt data", e);
        }
    }

    public byte[] decrypt(byte[] data) throws F1r3DriveError {
        try {
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(data);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new F1r3DriveError("Failed to decrypt data", e);
        }
    }

    private SecretKeySpec generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
        keyGen.init(128); // 128-bit key size
        SecretKey secretKey = keyGen.generateKey();
        byte[] keyBytes = secretKey.getEncoded();
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }

    private SecretKeySpec readOrGenerateKey(String pathToKey) throws NoSuchAlgorithmException, IOException {
        SecretKeySpec keySpec;
        if (new File(pathToKey).exists()) {
            keySpec = readKey(pathToKey);
        } else {
            keySpec = generateKey();
            saveKey(keySpec, pathToKey);
        }
        return keySpec;
    }

    private void saveKey(SecretKeySpec keySpec, String pathToKey) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(pathToKey)) {
            fos.write(keySpec.getEncoded());
        } catch (IOException e) {
            throw new IOException("Failed to save key file for " + KEY_ALGORITHM + ": " + pathToKey, e);
        }
    }

    private SecretKeySpec readKey(String pathToKey) throws IOException {
        try (FileInputStream fis = new FileInputStream(pathToKey)) {
            return new SecretKeySpec(fis.readAllBytes(), KEY_ALGORITHM);
        } catch (IOException e) {
            throw new IOException("Failed to read key file for " + KEY_ALGORITHM + ": " + pathToKey, e);
        }
    }

    public static void init(String keyToPath) {
        AESCipher.instance = new AESCipher(keyToPath);
    }

    public static AESCipher getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AES cipher not initialized");
        }
        return instance;
    }
}
