package com.wendao.system.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.wendao.common.utils.StringUtils;
import com.wendao.system.cache.ModelConfigCache;
import com.wendao.system.config.NewsFetchProperties;
import com.wendao.system.domain.NewsModelConfig;
import com.wendao.system.domain.NewsQueryExpansion;
import com.wendao.system.mapper.NewsQueryExpansionMapper;
import com.wendao.system.service.IQueryExpansionService;

/**
 * 查询扩展 服务层实现（模型从DB模型配置表按优先级选取，API Key AES加解密）
 *
 * @author wendao
 */
@Service
public class QueryExpansionServiceImpl implements IQueryExpansionService
{
    private static final Logger log = LoggerFactory.getLogger(QueryExpansionServiceImpl.class);

    @Autowired
    private NewsFetchProperties fetchProperties;

    @Autowired
    private ModelConfigCache modelConfigCache;

    @Autowired
    private NewsQueryExpansionMapper expansionMapper;

    @Autowired
    private com.wendao.system.utils.AiApiClient aiApiClient;

    private static final String USAGE_TYPE = "EXPANSION";

    @Override
    public List<String> expand(String keyword, Long keywordId)
    {
        List<String> result = new ArrayList<>();
        result.add(keyword);

        if (!fetchProperties.getQueryExpansion().isEnabled())
        {
            return result;
        }

        int maxTerms = fetchProperties.getQueryExpansion().getMaxTerms();

        // 1. 先从DB缓存查找
        List<NewsQueryExpansion> cached = expansionMapper.selectByKeywordId(keywordId);
        if (cached != null && !cached.isEmpty())
        {
            for (NewsQueryExpansion exp : cached)
            {
                if (result.size() >= maxTerms + 1) break;
                result.add(exp.getExpandedTerm());
            }
            return result;
        }

        // 2. 从模型配置缓存获取模型
        NewsModelConfig modelConfig = modelConfigCache.getModelConfig(USAGE_TYPE);
        if (modelConfig == null || StringUtils.isEmpty(modelConfig.getApiKey()))
        {
            log.debug("无可用AI模型配置（usageType={}），跳过查询扩展", USAGE_TYPE);
            return result;
        }

        // 3. 调用AI生成扩展词
        List<String> expanded = generateExpansions(keyword, modelConfig);
        if (expanded.isEmpty())
        {
            return result;
        }

        // 4. 持久化并返回
        for (String term : expanded)
        {
            if (result.size() >= maxTerms + 1) break;
            result.add(term);

            try
            {
                NewsQueryExpansion exp = new NewsQueryExpansion();
                exp.setKeywordId(keywordId);
                exp.setExpandedTerm(term);
                exp.setExpansionType("AI_GENERATED");
                exp.setIsActive(1);
                expansionMapper.insertExpansion(exp);
            }
            catch (Exception e)
            {
                log.warn("保存查询扩展词失败: {}", term, e);
            }
        }

        return result;
    }

    /**
     * 调用AI API生成同义词/相关词
     */
    private List<String> generateExpansions(String keyword, NewsModelConfig modelConfig)
    {
        List<String> expansions = new ArrayList<>();

        try
        {
            String prompt = "给定关键词\"" + keyword + "\"，返回2-3个同义词或紧密相关的搜索词（JSON数组格式）。\n"
                    + "例如：输入\"AI\" → [\"人工智能\",\"AI技术\",\"人工智能技术\"]\n"
                    + "仅返回JSON数组，不要其他内容。";

            // 使用 AiApiClient 统一调用
            java.util.List<java.util.Map<String, String>> msgList = new java.util.ArrayList<>();
            java.util.Map<String, String> sysMap = new java.util.HashMap<>();
            sysMap.put("role", "system");
            sysMap.put("content", "你是一个关键词扩展助手。仅返回JSON数组，不要任何其他文字。");
            msgList.add(sysMap);
            java.util.Map<String, String> userMap = new java.util.HashMap<>();
            userMap.put("role", "user");
            userMap.put("content", prompt);
            msgList.add(userMap);

            String content = aiApiClient.callNonStreaming(modelConfig, msgList, 100, 0.3, false);

            String jsonStr = content.trim();
            if (jsonStr.startsWith("```"))
            {
                jsonStr = jsonStr.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }

            JSONArray arr = JSON.parseArray(jsonStr);
            if (arr != null)
            {
                for (int i = 0; i < arr.size(); i++)
                {
                    String term = arr.getString(i);
                    if (StringUtils.isNotEmpty(term) && !term.equals(keyword))
                    {
                        expansions.add(term);
                    }
                }
            }

            log.info("查询扩展完成，模型: {}，关键词 [{}] → {}", modelConfig.getModelName(), keyword, expansions);
        }
        catch (Exception e)
        {
            log.error("查询扩展AI调用异常，关键词: {}", keyword, e);
        }

        return expansions;
    }
}
