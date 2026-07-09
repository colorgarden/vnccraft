package top.colorgarden.vnccraft.vnc;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * VNC Authentication type 2 DES encryption.
 * The VNC spec uses a specific key derivation:
 * password truncated to 8 chars, bit 7 cleared, bits reversed per byte.
 */
public final class VNCDesCipher {

    private VNCDesCipher() {}

    /**
     * Encrypt the 16-byte challenge with the password per VNC DES rules.
     */
    public static byte[] encrypt(byte[] challenge, String password) {
        byte[] key = deriveKey(password);
        try {
            Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DES"));
            return cipher.doFinal(challenge);
        } catch (Exception e) {
            throw new RuntimeException("VNC DES encryption failed", e);
        }
    }

    /**
     * Derive 8-byte DES key from password:
     * 1. Truncate/pad to 8 bytes
     * 2. Clear bit 7 of each byte
     * 3. Reverse the bits in each byte (VNC-specific)
     */
    private static byte[] deriveKey(String password) {
        byte[] key = new byte[8];
        byte[] pwdBytes;
        if (password != null) {
            pwdBytes = password.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        } else {
            pwdBytes = new byte[0];
        }

        // Copy up to 8 bytes, zero-pad the rest
        int len = Math.min(pwdBytes.length, 8);
        System.arraycopy(pwdBytes, 0, key, 0, len);

        // Clear bit 7 and reverse bits for each byte
        for (int i = 0; i < 8; i++) {
            key[i] = reverseBits((byte) (key[i] & 0x7F));
        }

        return key;
    }

    private static byte reverseBits(byte b) {
        int v = b & 0xFF;
        int result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 1) | (v & 1);
            v >>= 1;
        }
        return (byte) result;
    }
}
