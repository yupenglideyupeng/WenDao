package com.wendao.common.utils;

import com.wendao.common.config.WenDaoConfig;

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
    /** AES 算法 */
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    /** 默认 IV（16字节） */
    private static final byte[] DEFAULT_IV = "WenDao2026!@#$%^".getBytes(StandardCharsets.UTF_8);

    /**
     * 获取 AES 密钥（从配置读取）
     */
    private static String getKey()
    {
        return WenDaoConfig.getAesKey();
    }

    /**
     * 加密
     * @param plainText 明文
     * @return Base64 编码的密文，失败返回 null
     */
    public static String encrypt(String plainText)
    {
        if (plainText == null || plainText.isEmpty() || getKey() == null) return null;
        try
        {
            SecretKeySpec keySpec = new SecretKeySpec(getKey().getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(DEFAULT_IV);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 解密
     * @param cipherText Base64 编码的密文
     * @return 明文，失败返回 null
     */
    public static String decrypt(String cipherText)
    {
        if (cipherText == null || cipherText.isEmpty() || getKey() == null) return null;
        try
        {
            SecretKeySpec keySpec = new SecretKeySpec(getKey().getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(DEFAULT_IV);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
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
