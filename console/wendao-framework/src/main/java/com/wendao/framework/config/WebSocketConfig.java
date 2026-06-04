package com.wendao.framework.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.wendao.framework.websocket.NewsWebSocketHandler;
import com.wendao.framework.websocket.NewsWebSocketInterceptor;

/**
 * WebSocket配置
 *
 * @author wendao
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer
{
    @Autowired
    private NewsWebSocketHandler newsWebSocketHandler;

    @Autowired
    private NewsWebSocketInterceptor newsWebSocketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry)
    {
        registry.addHandler(newsWebSocketHandler, "/ws/news")
                .addInterceptors(newsWebSocketInterceptor)
                .setAllowedOrigins("*");
    }
}
