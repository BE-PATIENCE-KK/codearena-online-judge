package com.codearena.codearena.utils;


import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;


public class JwtUtil {


    private static final String SECRET_KEY = loadSecretKey();
    //生成token
    public static String createToken(String username){

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(
                        new Date(
                                System.currentTimeMillis()
                                        +1000*60*60*24
                        )
                )
                .signWith(key)
                .compact();

    }


    //解析token
    public static String getUsername(String token){

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();

    }


    private static final SecretKey key =
            Keys.hmacShaKeyFor(
                    SECRET_KEY.getBytes(StandardCharsets.UTF_8)
            );

    private static String loadSecretKey() {
        String secret = System.getenv("CODEARENA_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Set CODEARENA_JWT_SECRET before using JWT authentication.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("CODEARENA_JWT_SECRET must contain at least 32 UTF-8 bytes.");
        }
        return secret;
    }


}
