package com.wendao.common.utils;

import com.wendao.common.config.WenDaoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-256-CBC 加解密工具类
 * 用于数据库敏感字段（如 API Key）的加密存储
 *
 * @author wendao
 */
public class AesEncryptUtils
{
    private static final Logger log = LoggerFactory.getLogger(AesEncryptUtils.class);

    /** AES 算法 */
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    /** 默认 IV（16字节） */
    private static final byte[] DEFAULT_IV = "WenDao2026!@#$%^".getBytes(StandardCharsets.UTF_8);

    /**
     * 获取 AES 密钥（32字节，从配置读取后自动截取/补零）
     */
    private static byte[] getKeyBytes()
    {
        String key = WenDaoConfig.getAesKey();
        if (key == null) return null;
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        // AES-256 需要精确 32 字节
        if (keyBytes.length == 32) return keyBytes;
        // 长度不对：用 SHA-256 哈希派生 32 字节密钥（兼容任意长度配置）
        try
        {
            java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
            byte[] derived = sha256.digest(keyBytes);
            log.warn("AES密钥长度={}（非标准32字节），已通过SHA-256派生为32字节密钥。建议将 wendao.aes-key 改为恰好32字符。", keyBytes.length);
            return derived;
        }
        catch (Exception e)
        {
            log.error("SHA-256哈希派生密钥失败", e);
            return null;
        }
    }

    /**
     * 获取 AES 密钥字符串（已废弃，保留兼容）
     */
    @Deprecated
    private static String getKey()
    {
        byte[] bytes = getKeyBytes();
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }

    /**
     * 加密
     * @param plainText 明文
     * @return Base64 编码的密文，失败返回 null
     */
    public static String encrypt(String plainText)
    {
        if (plainText == null || plainText.isEmpty()) return null;
        byte[] keyBytes = getKeyBytes();
        if (keyBytes == null) return null;
        try
        {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(DEFAULT_IV);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        }
        catch (Exception e)
        {
            log.error("AES加密失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解密
     * @param cipherText Base64 编码的密文（或旧版明文）
     * @return 明文，失败返回 null
     */
    public static String decrypt(String cipherText)
    {
        if (cipherText == null || cipherText.isEmpty()) return null;
        byte[] keyBytes = getKeyBytes();
        if (keyBytes == null)
        {
            log.error("AES解密失败：未配置 aes-key，请在 application.yml 中设置 wendao.aes-key");
            return null;
        }
        try
        {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(DEFAULT_IV);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            // 兼容旧版：旧的 AES 密钥长度不对导致加密失败，API Key 以明文存储在 DB 中
            // 如果解密失败且值看起来像 API Key（不以 Base64 的纯字母数字结尾），
            // 则当作明文直接返回，下次保存时会用新的派生密钥重新加密
            if (cipherText.length() >= 20 && !cipherText.endsWith("=") && !cipherText.endsWith("=="))
            {
                log.warn("AES解密失败，密文看起来像是明文存储的旧版API Key，直接使用明文。下次保存模型配置时将自动重新加密。");
                return cipherText;
            }
            log.error("AES解密失败，密文长度={}，错误: {}。可能原因：1) AES密钥不一致 2) 密文不是AES加密的",
                    cipherText.length(), e.getMessage());
            return null;
        }
    }

    /**
     * 对 API Key 进行脱敏展示
     * 例：sk-xxx...xxx → sk-****3f82
     */
    public static String maskApiKey(String apiKey)
    {
        if (apiKey == null || apiKey.isEmpty()) return "";
        if (apiKey.length() <= 8) return "****";
        return apiKey.substring(0, 3) + "-****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 判断是否为脱敏值（以 **** 结尾且不以 sk- 开头后面接完整 key）
     * 用于判断编辑时用户是否修改了 key
     */
    public static boolean isMasked(String apiKey)
    {
        return apiKey != null && apiKey.contains("-****");
    }
}
