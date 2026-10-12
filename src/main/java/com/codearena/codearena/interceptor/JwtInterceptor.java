package com.codearena.codearena.interceptor;


import com.codearena.codearena.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codearena.codearena.entity.Result;


public class JwtInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {


        String token = request.getHeader("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return false;
        }

        token = token.substring(7);



        try {

            JwtUtil.getUsername(token);

        }catch(Exception e){

            writeUnauthorized(response);
            return false;

        }


        return true;

    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(401, "请先登录或重新登录")));
    }

}
