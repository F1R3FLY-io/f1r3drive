//package io.f1r3fly.f1r3drive.encryption;
//import javax.crypto.Cipher;
//import javax.crypto.KeyGenerator;
//import javax.crypto.SecretKey;
//import javax.crypto.spec.SecretKeySpec;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.Base64;
//import java.util.Arrays;
//
//public class ChunkedAESEncryption {
//    private static final int AES_BLOCK_SIZE = 16; // AES block size in bytes
//    private static final int CHUNK_SIZE = 1024 * AES_BLOCK_SIZE; // 16 KB
//
//    public static void main(String[] args) throws Exception {
//        // Generate AES key
//        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
//        keyGen.init(128); // 128-bit key size
//        SecretKey secretKey = keyGen.generateKey();
//
//        byte[] keyBytes = secretKey.getEncoded();
//        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
//
//        // Initialize Cipher for encryption
//        Cipher cipherEncrypt = Cipher.getInstance("AES/ECB/PKCS5Padding");
//        cipherEncrypt.init(Cipher.ENCRYPT_MODE, keySpec);
//
//        // Initialize Cipher for decryption
//        Cipher cipherDecrypt = Cipher.getInstance("AES/ECB/PKCS5Padding");
//        cipherDecrypt.init(Cipher.DECRYPT_MODE, keySpec);
//
//        // Encrypt large data and write to file
//        FileInputStream inputStream = new FileInputStream("large_data.txt");
//        FileOutputStream outputStreamEncrypted = new FileOutputStream("encrypted_data.txt");
//
//        byte[] buffer = new byte[CHUNK_SIZE];
//        int bytesRead;
//        while ((bytesRead = inputStream.read(buffer)) != -1) {
//            byte[] encryptedBytes = cipherEncrypt.doFinal(buffer, 0, padIfNeeded(bytesRead));
//            outputStreamEncrypted.write(Base64.getEncoder().encode(encryptedBytes));
//        }
//
//        inputStream.close();
//        outputStreamEncrypted.close();
//
//        // Decrypt encrypted data and compare with original data
//        FileInputStream encryptedInputStream = new FileInputStream("encrypted_data.txt");
//        FileOutputStream outputStreamDecrypted = new FileOutputStream("decrypted_data.txt");
//
//        byte[] encryptedBuffer = new byte[CHUNK_SIZE];
//        int encryptedBytesRead;
//        while ((encryptedBytesRead = encryptedInputStream.read(encryptedBuffer)) != -1) {
//            byte[] decodedBytes = Base64.getDecoder().decode(Arrays.copyOfRange(encryptedBuffer, 0, encryptedBytesRead));
//            byte[] decryptedBytes = cipherDecrypt.doFinal(decodedBytes);
//            outputStreamDecrypted.write(decryptedBytes);
//        }
//
//        encryptedInputStream.close();
//        outputStreamDecrypted.close();
//
//        // Compare decrypted data with original data
//        byte[] originalBytes = Files.readAllBytes(Paths.get("large_data.txt"));
//        byte[] decryptedBytes = Files.readAllBytes(Paths.get("decrypted_data.txt"));
//        boolean isEqual = Arrays.equals(originalBytes, decryptedBytes);
//
//        System.out.println("Decryption successful: " + isEqual);
//    }
//
//    private static int padIfNeeded(int bytesRead) {
//        return bytesRead % AES_BLOCK_SIZE == 0 ? bytesRead : bytesRead + (AES_BLOCK_SIZE - bytesRead % AES_BLOCK_SIZE);
//    }
//}
