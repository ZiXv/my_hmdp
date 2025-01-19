package com.hmdp.utils;

import com.auth0.jwt.algorithms.Algorithm;
import com.hmdp.entity.User;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.auth0.jwt.JWT;
import org.springframework.stereotype.Component;

@Component
public class JwtUtils {
    private static final String TOKEN_SECRET="S3cr3tK3y@2024!#Secure";

    public String createJwtToken(User user) {
        // 定义 JWT 的有效期（30分钟）
        long expirationTime = 30 * 60 * 1000L; //
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTime);
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("nickName", user.getNickName());

        // 使用 JJWT 库生成 Token
        return JWT.create()
                .withIssuer("auth0").withClaim("id",user.getId())
                .withExpiresAt(expiration)
                .withClaim("nickname",user.getNickName())
                .sign(Algorithm.HMAC256(TOKEN_SECRET));

    }
}
