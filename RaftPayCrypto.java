import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RaftPay RSA 加解密工具
 *
 * 算法: RSA/ECB/PKCS1Padding
 * 私钥格式: PKCS8 (Base64)
 * 公钥格式: X509/SPKI (Base64)
 * 分段加密块: keySize - 11 字节 (2048位密钥 = 245字节)
 * 分段解密块: keySize 字节 (2048位密钥 = 256字节)
 *
 * Java 8+ 兼容，零第三方依赖
 */
public class RaftPayCrypto {

    /**
     * 使用商户私钥加密数据（商户 → 平台）
     *
     * @param plainText        明文 JSON 字符串
     * @param privateKeyBase64 商户私钥（Base64 编码）
     * @return Base64 编码的加密数据
     */
    public static String encryptWithPrivateKey(String plainText, String privateKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(cleanKey(privateKeyBase64));
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);

        int keySize = ((RSAKey) privateKey).getModulus().bitLength() / 8;
        int maxBlock = keySize - 11; // PKCS1Padding 占 11 字节

        byte[] data = plainText.getBytes("UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offset = 0;
        while (offset < data.length) {
            int blockLen = Math.min(maxBlock, data.length - offset);
            byte[] block = cipher.doFinal(data, offset, blockLen);
            out.write(block);
            offset += blockLen;
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /**
     * 使用平台公钥解密数据（平台 → 商户，用于回调解密）
     *
     * @param encryptedBase64 Base64 编码的加密数据
     * @param publicKeyBase64 平台公钥（Base64 编码）
     * @return 解密后的 JSON 字符串
     */
    public static String decryptWithPublicKey(String encryptedBase64, String publicKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(cleanKey(publicKeyBase64));
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);

        int keySize = ((RSAKey) publicKey).getModulus().bitLength() / 8;

        byte[] data = Base64.getDecoder().decode(encryptedBase64);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offset = 0;
        while (offset < data.length) {
            int blockLen = Math.min(keySize, data.length - offset);
            byte[] block = cipher.doFinal(data, offset, blockLen);
            out.write(block);
            offset += blockLen;
        }
        return out.toString("UTF-8");
    }

    /**
     * 清理密钥字符串，移除 PEM 头尾和空白
     */
    private static String cleanKey(String key) {
        return key.replaceAll("-----.*?-----", "").replaceAll("\\s", "");
    }
}
