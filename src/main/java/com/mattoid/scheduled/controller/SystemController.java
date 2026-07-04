package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.SysPermission;
import com.mattoid.scheduled.entity.SysRole;
import com.mattoid.scheduled.entity.SysUser;
import com.mattoid.scheduled.entity.SysUserRole;
import com.mattoid.scheduled.mapper.SysPermissionMapper;
import com.mattoid.scheduled.mapper.SysRoleMapper;
import com.mattoid.scheduled.mapper.SysUserMapper;
import com.mattoid.scheduled.mapper.SysUserRoleMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public SystemController(SysUserMapper sysUserMapper,
                            SysRoleMapper sysRoleMapper,
                            SysPermissionMapper sysPermissionMapper,
                            SysUserRoleMapper sysUserRoleMapper,
                            PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    // ---- 用户 ----
    @PreAuthorize("hasAuthority('system:user')")
    @GetMapping("/user/page")
    public Result<PageResult<SysUser>> userPage(PageQuery query,
                                                @RequestParam(required = false) String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('system:user')")
    @PostMapping("/user")
    public Result<Boolean> createUser(@RequestBody SysUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return Result.ok(sysUserMapper.insert(user) > 0);
    }

    @PreAuthorize("hasAuthority('system:user')")
    @PutMapping("/user/{id}")
    public Result<Boolean> updateUser(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        return Result.ok(sysUserMapper.updateById(user) > 0);
    }

    @PreAuthorize("hasAuthority('system:user')")
    @DeleteMapping("/user/{id}")
    public Result<Boolean> deleteUser(@PathVariable Long id) {
        return Result.ok(sysUserMapper.deleteById(id) > 0);
    }

    @PreAuthorize("hasAuthority('system:user')")
    @PostMapping("/user/{userId}/roles")
    public Result<Boolean> assignUserRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        sysUserRoleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
        return Result.ok(true);
    }

    // ---- 角色 ----
    @PreAuthorize("hasAuthority('system:role')")
    @GetMapping("/role/list")
    public Result<List<SysRole>> roleList() {
        return Result.ok(sysRoleMapper.selectList(null));
    }

    @PreAuthorize("hasAuthority('system:role')")
    @PostMapping("/role")
    public Result<Boolean> createRole(@RequestBody SysRole role) {
        return Result.ok(sysRoleMapper.insert(role) > 0);
    }

    @PreAuthorize("hasAuthority('system:role')")
    @PutMapping("/role/{id}")
    public Result<Boolean> updateRole(@PathVariable Long id, @RequestBody SysRole role) {
        role.setId(id);
        return Result.ok(sysRoleMapper.updateById(role) > 0);
    }

    @PreAuthorize("hasAuthority('system:role')")
    @DeleteMapping("/role/{id}")
    public Result<Boolean> deleteRole(@PathVariable Long id) {
        return Result.ok(sysRoleMapper.deleteById(id) > 0);
    }

    // ---- 权限 ----
    @PreAuthorize("hasAuthority('system:role')")
    @GetMapping("/permission/list")
    public Result<List<SysPermission>> permissionList() {
        return Result.ok(sysPermissionMapper.selectList(null));
    }
}
