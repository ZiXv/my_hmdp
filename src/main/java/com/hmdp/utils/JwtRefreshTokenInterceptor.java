package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JwtRefreshTokenInterceptor implements HandlerInterceptor {

    private static final String SECRET_KEY = "S3cr3tK3y@2024!#Secure"; // 替换为你的密钥

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的 token
        String token = request.getHeader("authorization");

        // 2. 如果 token 是空，直接放行（未登录状态可以交给 LoginInterceptor 或其他逻辑处理）
        if (StrUtil.isBlank(token)) {
            return true;
        }

        try {
            // 3. 验证并解析 token
            DecodedJWT jwt = JWT.require(com.auth0.jwt.algorithms.Algorithm.HMAC256(SECRET_KEY))
                    .build()
                    .verify(token);

            // 4. 提取用户信息
            Long userId = jwt.getClaim("id").asLong();
            String nickname = jwt.getClaim("nickname").asString();

            // 5. 保存到 ThreadLocal（UserHolder）
            UserDTO userDTO = new UserDTO();
            userDTO.setId(userId);
            userDTO.setNickName(nickname);
            UserHolder.saveUser(userDTO);

            // 6. 放行
            return true;

        } catch (TokenExpiredException e) {
            // Token 过期
            response.setStatus(401); // 未授权
            response.getWriter().write("Token expired");
            return false;
        } catch (JWTDecodeException e) {
            // Token 无效
            response.setStatus(401);
            response.getWriter().write("Invalid token");
            return false;
        } catch (Exception e) {
            // 其他异常
            response.setStatus(500);
            response.getWriter().write("Server error");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理 ThreadLocal 避免内存泄漏
        UserHolder.removeUser();
    }
}
