package com.wendao.system.cache;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.wendao.common.utils.AesEncryptUtils;
import com.wendao.common.utils.StringUtils;
import com.wendao.system.domain.NewsModelConfig;
import com.wendao.system.mapper.NewsModelConfigMapper;

/**
 * AI 模型配置缓存层
 * <p>
 * 从 news_model_config 表按优先级加载模型配置，缓存到 Redis。
 * 缓存 TTL = 5 分钟，增删改操作自动失效缓存。
 * <p>
 * API Key 在数据库中 AES 加密存储，缓存中解密为明文（Redis 内网安全）。
 *
 * @author wendao
 */
@Component
public class ModelConfigCache
{
    private static final Logger log = LoggerFactory.getLogger(ModelConfigCache.class);

    /** 缓存前缀 */
    private static final String CACHE_PREFIX = "model_config:";

    /** 缓存过期时间（秒） */
    private static final long CACHE_TTL_SECONDS = 300;

    @Autowired
    private NewsModelConfigMapper mapper;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    /**
     * 按适用场景获取优先级最高的激活模型（已解密 API Key）
     *
     * @param usageType 适用场景：INTERPRET / ANALYSIS / EXPANSION
     * @return 解密后的模型配置，无可用模型时返回 null
     */
    public NewsModelConfig getModelConfig(String usageType)
    {
        String cacheKey = CACHE_PREFIX + usageType;

        // 1. 尝试从 Redis 读取
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof NewsModelConfig)
        {
            log.debug("命中模型缓存 usageType={}", usageType);
            return (NewsModelConfig) cached;
        }

        // 2. 缓存未命中，从 DB 加载
        List<NewsModelConfig> list = mapper.selectByUsageType(usageType);
        if (list == null || list.isEmpty())
        {
            log.warn("未找到可用的模型配置 usageType={}，请检查 news_model_config 表", usageType);
            return null;
        }

        // 3. 取优先级最高的，解密 API Key
        NewsModelConfig config = list.get(0);
        NewsModelConfig decrypted = decryptAndCopy(config);

        // 4. 写入 Redis 缓存
        redisTemplate.opsForValue().set(cacheKey, decrypted, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("加载模型配置 usageType={} model={} priority={}", usageType, decrypted.getModelName(), decrypted.getPriority());

        return decrypted;
    }

    /**
     * 按适用场景获取所有激活的模型列表（已解密，按优先级排序）
     * 调用方可按顺序依次重试（一个模型失败则使用下一个）
     *
     * @param usageType 适用场景
     * @return 解密后的模型配置列表
     */
    @SuppressWarnings("unchecked")
    public List<NewsModelConfig> getModelConfigList(String usageType)
    {
        String cacheKey = CACHE_PREFIX + "list:" + usageType;

        // 1. 尝试从 Redis 读取
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List)
        {
            return (List<NewsModelConfig>) cached;
        }

        // 2. 从 DB 加载
        List<NewsModelConfig> list = mapper.selectByUsageType(usageType);
        if (list != null)
        {
            for (NewsModelConfig config : list)
            {
                decryptInPlace(config);
            }
        }

        // 3. 写入缓存
        if (list != null && !list.isEmpty())
        {
            redisTemplate.opsForValue().set(cacheKey, list, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        }

        return list;
    }

    /**
     * 失效指定场景的缓存（数据变更时调用）
     */
    public void evictCache(String usageType)
    {
        redisTemplate.delete(CACHE_PREFIX + usageType);
        redisTemplate.delete(CACHE_PREFIX + "list:" + usageType);
    }

    /**
     * 失效所有场景的缓存
     */
    public void evictAllCache()
    {
        redisTemplate.delete(CACHE_PREFIX + "INTERPRET");
        redisTemplate.delete(CACHE_PREFIX + "list:INTERPRET");
        redisTemplate.delete(CACHE_PREFIX + "ANALYSIS");
        redisTemplate.delete(CACHE_PREFIX + "list:ANALYSIS");
        redisTemplate.delete(CACHE_PREFIX + "EXPANSION");
        redisTemplate.delete(CACHE_PREFIX + "list:EXPANSION");
        redisTemplate.delete(CACHE_PREFIX + "ALL");
        redisTemplate.delete(CACHE_PREFIX + "list:ALL");
    }

    /**
     * 获取全局模型状态（供前端判断 AI 功能是否可用）
     */
    public ModelStatus getModelStatus()
    {
        ModelStatus status = new ModelStatus();

        // 统计所有模型
        com.wendao.system.domain.NewsModelConfig query = new com.wendao.system.domain.NewsModelConfig();
        List<NewsModelConfig> all = mapper.selectList(query);
        status.setTotalModels(all != null ? all.size() : 0);
        status.setActiveModels(all != null ? (int) all.stream().filter(m -> m.getIsActive() != null && m.getIsActive() == 1).count() : 0);

        // 检查各场景
        String[] scenes = {"INTERPRET", "ANALYSIS", "EXPANSION"};
        for (String scene : scenes)
        {
            NewsModelConfig config = getModelConfig(scene);
            status.getScenes().put(scene, config != null && StringUtils.isNotEmpty(config.getApiKey()));
            status.getModelNames().put(scene, config != null ? config.getModelName() : null);
        }

        return status;
    }

    /**
     * 解密并返回新对象（不修改原对象）
     */
    private NewsModelConfig decryptAndCopy(NewsModelConfig source)
    {
        NewsModelConfig copy = new NewsModelConfig();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setProvider(source.getProvider());
        copy.setApiUrl(source.getApiUrl());
        copy.setModelName(source.getModelName());
        copy.setPriority(source.getPriority());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setTemperature(source.getTemperature());
        copy.setSupportJsonMode(source.getSupportJsonMode());
        copy.setSupportStream(source.getSupportStream());
        copy.setUsageType(source.getUsageType());
        copy.setApiFormat(source.getApiFormat());
        copy.setTimeoutMs(source.getTimeoutMs());
        copy.setRetryCount(source.getRetryCount());
        copy.setIsActive(source.getIsActive());

        // 解密 API Key
        if (StringUtils.isNotEmpty(source.getApiKey()))
        {
            copy.setApiKey(AesEncryptUtils.decrypt(source.getApiKey()));
        }

        return copy;
    }

    /**
     * 原地解密（修改原对象）
     */
    private void decryptInPlace(NewsModelConfig config)
    {
        if (StringUtils.isNotEmpty(config.getApiKey()))
        {
            config.setApiKey(AesEncryptUtils.decrypt(config.getApiKey()));
        }
    }
}
