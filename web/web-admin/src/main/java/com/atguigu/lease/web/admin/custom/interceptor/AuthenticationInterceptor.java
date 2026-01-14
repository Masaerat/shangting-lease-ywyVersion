package com.atguigu.lease.web.admin.custom.interceptor;

import com.atguigu.lease.common.login.LoginUser;
import com.atguigu.lease.common.login.LoginUserHolder;
import com.atguigu.lease.common.login.SysLoginUser;
import com.atguigu.lease.common.login.SysLoginUserHolder;
import com.atguigu.lease.common.utils.JwtUtil;
import com.atguigu.lease.model.entity.SystemUser;
import com.atguigu.lease.web.admin.mapper.SystemUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * jwt拦截器，验证了请求的请求头的access-token中的token是否合法，用来判断是否是合法的用户。
 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    @Autowired
    private SystemUserMapper systemUserMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 前端登录后，后续请求都将JWT，放置于HTTP请求的Header中，其Header的key为`access-token`。
        String token = request.getHeader("access-token");

        // 解析该token，如果成功则放行，如果失败，则拦截。因为在parseToken中抛出异常，所以这里不需要显式拦截。
        Claims claims = JwtUtil.parseToken(token);

        Long userId = claims.get("userId", Long.class);//从token中解析出userId
        String username = claims.get("username", String.class);//从token中解析出username

        //查询用户表 区分类型
        SystemUser systemUser = systemUserMapper.selectById(userId);
        SysLoginUser sysLoginUser = new SysLoginUser();
        sysLoginUser.setUsername(username);
        sysLoginUser.setUserId(userId);
        sysLoginUser.setType(sysLoginUser.getType());
        SysLoginUserHolder.setSysLoginUser(sysLoginUser);
        // 放行。
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //释放线程资源。
        SysLoginUserHolder.clear();
    }
}
