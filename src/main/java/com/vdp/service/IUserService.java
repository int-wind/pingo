package com.vdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vdp.dto.LoginFormDTO;
import com.vdp.dto.Result;
import com.vdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

    Result logout(String token);
}
