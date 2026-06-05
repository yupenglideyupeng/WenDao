package com.wendao.system.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.wendao.common.utils.StringUtils;
import com.wendao.system.config.NewsAiProperties;
import com.wendao.system.config.NewsFetchProperties;
import com.wendao.system.domain.NewsQueryExpansion;
import com.wendao.system.mapper.NewsQueryExpansionMapper;
import com.wendao.system.service.IQueryExpansionService;

/**
 * 查询扩展 服务层实现
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
    private NewsAiProperties aiProperties;

    @Autowired
    private NewsQueryExpansionMapper expansionMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<String> expand(String keyword, Long keywordId)
    {
        List<String> result = new ArrayList<>();
        result.add(keyword); // 始终包含原始词

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
                if (result.size() >= maxTerms + 1) break; // +1 因为包含原始词
                result.add(exp.getExpandedTerm());
            }
            return result;
        }

        // 2. 无缓存，调用AI生成扩展词
        List<String> expanded = generateExpansions(keyword);
        if (expanded.isEmpty())
        {
            return result;
        }

        // 3. 持久化并返回
        for (String term : expanded)
        {
            if (result.size() >= maxTerms + 1) break;
            result.add(term);

            // 保存到DB
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
     * 调用DeepSeek API生成同义词/相关词
     */
    private List<String> generateExpansions(String keyword)
    {
        List<String> expansions = new ArrayList<>();

        if (!aiProperties.isEnabled() || StringUtils.isEmpty(aiProperties.getApiKey()))
        {
            return expansions;
        }

        try
        {
            String prompt = "给定关键词\"" + keyword + "\"，返回2-3个同义词或紧密相关的搜索词（JSON数组格式）。\n"
                    + "例如：输入\"AI\" → [\"人工智能\",\"AI技术\",\"人工智能技术\"]\n"
                    + "仅返回JSON数组，不要其他内容。";

            com.alibaba.fastjson2.JSONObject body = new com.alibaba.fastjson2.JSONObject();
            body.put("model", aiProperties.getModel());
            body.put("temperature", 0.3);
            body.put("max_tokens", 100);
            body.put("stream", false);

            JSONArray messages = new JSONArray();
            com.alibaba.fastjson2.JSONObject sysMsg = new com.alibaba.fastjson2.JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是一个关键词扩展助手。仅返回JSON数组，不要任何其他文字。");
            messages.add(sysMsg);

            com.alibaba.fastjson2.JSONObject userMsg = new com.alibaba.fastjson2.JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            body.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + aiProperties.getApiKey());

            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiProperties.getApiUrl(), entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful())
            {
                log.error("查询扩展AI请求失败，状态码: {}", response.getStatusCode().value());
                return expansions;
            }

            com.alibaba.fastjson2.JSONObject respJson = JSON.parseObject(response.getBody());
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) return expansions;

            String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
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

            log.info("查询扩展完成，关键词 [{}] → {}", keyword, expansions);
        }
        catch (Exception e)
        {
            log.error("查询扩展AI调用异常，关键词: {}", keyword, e);
        }

        return expansions;
    }
}
