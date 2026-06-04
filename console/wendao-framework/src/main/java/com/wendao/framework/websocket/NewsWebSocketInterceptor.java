package com.wendao.framework.websocket;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import com.wendao.common.core.domain.model.LoginUser;
import com.wendao.framework.web.service.TokenService;

/**
 * WebSocket握手拦截器 - JWT认证
 *
 * @author wendao
 */
@Component
public class NewsWebSocketInterceptor implements HandshakeInterceptor
{
    private static final Logger log = LoggerFactory.getLogger(NewsWebSocketInterceptor.class);

    @Autowired
    private TokenService tokenService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception
    {
        if (request instanceof ServletServerHttpRequest)
        {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token != null && !token.isEmpty())
            {
                LoginUser loginUser = tokenService.getLoginUserByToken(token);
                if (loginUser != null)
                {
                    attributes.put("loginUser", loginUser);
                    log.info("WebSocket握手成功: {}", loginUser.getUsername());
                    return true;
                }
            }
            log.warn("WebSocket握手失败: token无效");
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception)
    {
    }
}
