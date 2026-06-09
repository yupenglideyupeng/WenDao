package com.wendao.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.wendao.common.utils.AesEncryptUtils;
import com.wendao.common.utils.StringUtils;
import com.wendao.system.domain.NewsModelConfig;
import com.wendao.system.mapper.NewsModelConfigMapper;
import com.wendao.system.service.INewsModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI模型配置 服务层实现
 *
 * @author wendao
 */
@Service
public class NewsModelConfigServiceImpl implements INewsModelConfigService
{
    private static final Logger log = LoggerFactory.getLogger(NewsModelConfigServiceImpl.class);

    @Autowired
    private NewsModelConfigMapper mapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<NewsModelConfig> selectList(NewsModelConfig config)
    {
        List<NewsModelConfig> list = mapper.selectList(config);
        // 返回前端前脱敏 apiKey
        for (NewsModelConfig item : list)
        {
            item.setApiKey(AesEncryptUtils.maskApiKey(item.getApiKey()));
        }
        return list;
    }

    @Override
    public NewsModelConfig selectById(Long id)
    {
        NewsModelConfig config = mapper.selectById(id);
        if (config != null)
        {
            // 详情也脱敏
            config.setApiKey(AesEncryptUtils.maskApiKey(config.getApiKey()));
        }
        return config;
    }

    @Override
    public List<NewsModelConfig> selectByUsageType(String usageType)
    {
        return mapper.selectByUsageType(usageType);
    }

    @Override
    public int insert(NewsModelConfig config)
    {
        // 加密 apiKey
        if (StringUtils.isNotEmpty(config.getApiKey()))
        {
            String encrypted = AesEncryptUtils.encrypt(config.getApiKey());
            if (encrypted != null)
            {
                config.setApiKey(encrypted);
            }
        }
        return mapper.insert(config);
    }

    @Override
    public int update(NewsModelConfig config)
    {
        // 如果 apiKey 是脱敏值（包含 ****），说明用户未修改，保留原值
        if (StringUtils.isNotEmpty(config.getApiKey()))
        {
            if (AesEncryptUtils.isMasked(config.getApiKey()))
            {
                // 查询原记录的加密 key，保持不变
                NewsModelConfig old = mapper.selectById(config.getId());
                if (old != null)
                {
                    config.setApiKey(old.getApiKey());
                }
            }
            else
            {
                // 用户输入了新 key，加密存储
                String encrypted = AesEncryptUtils.encrypt(config.getApiKey());
                if (encrypted != null)
                {
                    config.setApiKey(encrypted);
                }
            }
        }
        return mapper.update(config);
    }

    @Override
    public int deleteByIds(Long[] ids)
    {
        return mapper.deleteByIds(ids);
    }

    @Override
    public Map<String, Object> testConnection(Long id)
    {
        Map<String, Object> result = new HashMap<>();

        NewsModelConfig config = mapper.selectById(id);
        if (config == null)
        {
            result.put("success", false);
            result.put("message", "配置不存在");
            return result;
        }

        // 解密 apiKey
        String apiKey = AesEncryptUtils.decrypt(config.getApiKey());
        if (StringUtils.isEmpty(apiKey))
        {
            result.put("success", false);
            result.put("message", "API Key 解密失败");
            return result;
        }

        try
        {
            // 构造最小化测试请求
            JSONObject body = new JSONObject();
            body.put("model", config.getModelName());
            body.put("max_tokens", 5);
            body.put("temperature", 0.1);
            body.put("stream", false);

            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "hi");
            messages.add(userMsg);
            body.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            int timeout = config.getTimeoutMs() != null ? config.getTimeoutMs() : 10000;
            // RestTemplate 默认超时可能不够，简单设置连接超时
            restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                setConnectTimeout(timeout);
                setReadTimeout(timeout);
            }});

            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    config.getApiUrl(), entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
            {
                JSONObject respJson = JSON.parseObject(response.getBody());
                JSONArray choices = respJson.getJSONArray("choices");
                if (choices != null && !choices.isEmpty())
                {
                    String content = choices.getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                    result.put("success", true);
                    result.put("message", "连接成功，模型响应: " + (content != null ? content : "(空)"));
                    result.put("modelName", config.getModelName());
                }
                else
                {
                    result.put("success", false);
                    result.put("message", "API 返回异常：无 choices 字段，可能接口地址不正确");
                }
            }
            else
            {
                result.put("success", false);
                result.put("message", "HTTP " + response.getStatusCode().value() + "，请检查 API 地址和密钥");
            }
        }
        catch (org.springframework.web.client.ResourceAccessException e)
        {
            result.put("success", false);
            result.put("message", "连接超时或无法访问：" + e.getMessage());
        }
        catch (Exception e)
        {
            log.error("测试模型连接失败，id={}", id, e);
            result.put("success", false);
            result.put("message", "连接失败：" + e.getMessage());
        }

        return result;
    }
}
