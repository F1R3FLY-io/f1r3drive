package io.f1r3fly.f1r3drive.filesystem.deployable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import io.f1r3fly.f1r3drive.app.linux.fuse.FuseAdapter;
import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.encryption.AESCipher;
import java.nio.file.Path;
import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FetchedFileTest {

  @TempDir Path tempDir;

  @Test
  void fetchedEncryptedBytesAreReadBackAsPlaintext() throws Exception {
    AESCipher.init(tempDir.resolve("cipher.key").toString());
    byte[] plaintext = "secret content".getBytes();
    byte[] encrypted = AESCipher.getInstance().encrypt(plaintext);

    FetchedFile file =
        new FetchedFile(
            mock(BlockchainContext.class),
            "secret.txt.encrypted",
            null,
            System.currentTimeMillis());

    int initializedBytes = file.initFromBytes(encrypted, 0);

    assertEquals(plaintext.length, initializedBytes);
    assertEquals(plaintext.length, file.getSize());

    Pointer readBuffer = Memory.allocate(jnr.ffi.Runtime.getSystemRuntime(), plaintext.length);
    int readBytes = file.read(FuseAdapter.fromFusePointer(readBuffer), plaintext.length, 0);
    byte[] actual = new byte[readBytes];
    readBuffer.get(0, actual, 0, readBytes);

    assertArrayEquals(plaintext, actual);
  }
}
