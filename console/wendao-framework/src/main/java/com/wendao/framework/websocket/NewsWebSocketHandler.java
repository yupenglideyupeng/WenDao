package com.wendao.framework.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.context.event.EventListener;
import com.alibaba.fastjson2.JSON;
import com.wendao.common.core.domain.model.LoginUser;
import com.wendao.system.event.NewsFetchedEvent;
import com.wendao.system.domain.NewsArticle;

/**
 * 新闻WebSocket处理器 - 实时推送新闻
 *
 * @author wendao
 */
@Component
public class NewsWebSocketHandler extends TextWebSocketHandler
{
    private static final Logger log = LoggerFactory.getLogger(NewsWebSocketHandler.class);

    /** 在线WebSocket客户端 */
    private static final Map<String, WebSocketSession> CLIENTS = new ConcurrentHashMap<>();

    /**
     * 连接建立后
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception
    {
        LoginUser loginUser = (LoginUser) session.getAttributes().get("loginUser");
        String username = loginUser != null ? loginUser.getUsername() : "unknown";
        CLIENTS.put(session.getId(), session);
        log.info("WebSocket客户端连接: {} (用户: {}), 当前在线: {}", session.getId(), username, CLIENTS.size());
    }

    /**
     * 处理文本消息（心跳等）
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception
    {
        String payload = message.getPayload();
        if ("ping".equals(payload))
        {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    /**
     * 连接关闭后
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception
    {
        CLIENTS.remove(session.getId());
        log.info("WebSocket客户端断开: {}, 当前在线: {}", session.getId(), CLIENTS.size());
    }

    /**
     * 传输错误
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception
    {
        CLIENTS.remove(session.getId());
        log.error("WebSocket传输错误: {}", session.getId(), exception);
    }

    /**
     * 广播消息到所有客户端
     */
    public void broadcast(String message)
    {
        CLIENTS.forEach((id, session) -> {
            if (session.isOpen())
            {
                try
                {
                    synchronized (session)
                    {
                        session.sendMessage(new TextMessage(message));
                    }
                }
                catch (IOException e)
                {
                    log.error("WebSocket发送消息失败: {}", id, e);
                }
            }
            else
            {
                CLIENTS.remove(id);
            }
        });
    }

    /**
     * 广播新文章
     */
    public void broadcastArticle(NewsArticle article)
    {
        String message = JSON.toJSONString(Map.of(
            "type", "NEW_ARTICLE",
            "data", article,
            "timestamp", System.currentTimeMillis()
        ));
        broadcast(message);
        log.info("WebSocket推送文章: {}", article.getTitle());
    }

    /**
     * 获取在线客户端数量
     */
    public int getOnlineCount()
    {
        return CLIENTS.size();
    }

    /**
     * 监听新闻抓取事件，自动推送
     */
    @EventListener
    public void onNewsFetched(NewsFetchedEvent event)
    {
        if (event.getArticles() != null)
        {
            for (NewsArticle article : event.getArticles())
            {
                broadcastArticle(article);
            }
            log.info("事件驱动推送 {} 篇文章到 {} 个客户端", event.getArticles().size(), CLIENTS.size());
        }
    }
}
