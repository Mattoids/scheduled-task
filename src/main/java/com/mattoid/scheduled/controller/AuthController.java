package com.mattoid.scheduled.controller;

import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.LoginRequest;
import com.mattoid.scheduled.dto.LoginResponse;
import com.mattoid.scheduled.entity.SysUser;
import com.mattoid.scheduled.mapper.SysUserMapper;
import com.mattoid.scheduled.security.JwtTokenProvider;
import com.mattoid.scheduled.vo.CurrentUserVo;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserMapper sysUserMapper;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider,
                          SysUserMapper sysUserMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sysUserMapper = sysUserMapper;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        User user = (User) auth.getPrincipal();
        SysUser sysUser = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, user.getUsername())
        );
        List<String> permissions = sysUserMapper.selectPermissionsByUserId(sysUser.getId());
        String token = jwtTokenProvider.generateToken(user.getUsername(), sysUser.getId());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUsername(user.getUsername());
        response.setNickname(sysUser.getNickname());
        response.setPermissions(permissions);
        return Result.ok(response);
    }

    @GetMapping("/me")
    public Result<CurrentUserVo> me() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysUser sysUser = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, user.getUsername())
        );
        CurrentUserVo vo = new CurrentUserVo();
        vo.setUserId(sysUser.getId());
        vo.setUsername(sysUser.getUsername());
        vo.setNickname(sysUser.getNickname());
        vo.setPermissions(sysUserMapper.selectPermissionsByUserId(sysUser.getId()));
        return Result.ok(vo);
    }
}
