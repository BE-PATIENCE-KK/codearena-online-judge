package com.codearena.codearena.config;


import com.codearena.codearena.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


/**
 * 注册 JWT 拦截器。
 *
 * 说明：token 保存在 localStorage 中，浏览器只有在 fetch/XHR 请求时才会带上
 * Authorization 头，直接输入网址访问页面时不会带任何 token。
 * 因此这里把“网页入口”放行，页面加载后再由页面内的 JS 调用 /user/info 校验登录状态，
 * 未登录时由页面跳转回 /login.html。
 *
 * 需要保护的接口（/user/**、/submission/** 等）故意不写入下面的白名单。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {


    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {


        registry.addInterceptor(
                        new JwtInterceptor()
                )
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 网页入口：页面先加载，再由页面内 JS 校验登录状态
                        "/login.html",
                        "/register.html",
                        "/problem-list.html",
                        "/problem.html",
                        "/submission.html",
                        // 登录 / 注册接口：必须允许未登录访问
                        "/api/auth/login",
                        "/api/auth/register",
                        // 题目查询接口：保持原有设计
                        "/problem/list",
                        "/problem/*"

                        // 注意：/user/** 与 /submission/** 不在白名单中，继续受 JWT 保护
                );

    }

}