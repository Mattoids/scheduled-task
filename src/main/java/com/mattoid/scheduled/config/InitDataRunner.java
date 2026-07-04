package com.mattoid.scheduled.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class InitDataRunner implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final PasswordEncoder passwordEncoder;

    public InitDataRunner(SysUserMapper sysUserMapper,
                          SysRoleMapper sysRoleMapper,
                          SysPermissionMapper sysPermissionMapper,
                          SysUserRoleMapper sysUserRoleMapper,
                          SysRolePermissionMapper sysRolePermissionMapper,
                          PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        initRoles();
        initPermissions();
        initAdmin();
        ensureAdminHasAllPermissions();
        log.info("初始化数据完成");
    }

    private void initRoles() {
        List<SysRole> roles = Arrays.asList(
                createRole("ADMIN", "系统管理员"),
                createRole("OPERATOR", "运维人员"),
                createRole("VIEWER", "只读用户")
        );
        for (SysRole role : roles) {
            if (sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, role.getRoleCode())) == 0) {
                sysRoleMapper.insert(role);
            }
        }
    }

    private SysRole createRole(String code, String name) {
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setStatus(1);
        return role;
    }

    private void initPermissions() {
        List<SysPermission> permissions = Arrays.asList(
                createPermission(1L, "task:view", "任务查看", "MENU", 0L, 1),
                createPermission(2L, "task:create", "任务创建", "BUTTON", 1L, 2),
                createPermission(3L, "task:edit", "任务编辑", "BUTTON", 1L, 3),
                createPermission(4L, "task:delete", "任务删除", "BUTTON", 1L, 4),
                createPermission(5L, "task:trigger", "任务手动触发", "BUTTON", 1L, 5),
                createPermission(6L, "datasource:view", "数据源查看", "MENU", 0L, 10),
                createPermission(7L, "datasource:create", "数据源创建", "BUTTON", 6L, 11),
                createPermission(8L, "datasource:edit", "数据源编辑", "BUTTON", 6L, 12),
                createPermission(9L, "datasource:delete", "数据源删除", "BUTTON", 6L, 13),
                createPermission(10L, "email:view", "邮箱配置查看", "MENU", 0L, 20),
                createPermission(11L, "email:create", "邮箱配置创建", "BUTTON", 10L, 21),
                createPermission(12L, "email:edit", "邮箱配置编辑", "BUTTON", 10L, 22),
                createPermission(13L, "email:delete", "邮箱配置删除", "BUTTON", 10L, 23),
                createPermission(14L, "template:view", "模板查看", "MENU", 0L, 30),
                createPermission(15L, "template:create", "模板创建", "BUTTON", 14L, 31),
                createPermission(16L, "template:edit", "模板编辑", "BUTTON", 14L, 32),
                createPermission(17L, "template:delete", "模板删除", "BUTTON", 14L, 33),
                createPermission(18L, "log:view", "日志查看", "MENU", 0L, 40),
                createPermission(19L, "system:user", "用户管理", "MENU", 0L, 50),
                createPermission(20L, "system:role", "角色管理", "MENU", 0L, 51),
                createPermission(21L, "wecomApp:view", "企业微信应用查看", "MENU", 0L, 60),
                createPermission(22L, "wecomApp:create", "企业微信应用创建", "BUTTON", 21L, 61),
                createPermission(23L, "wecomApp:edit", "企业微信应用编辑", "BUTTON", 21L, 62),
                createPermission(24L, "wecomApp:delete", "企业微信应用删除", "BUTTON", 21L, 63),
                createPermission(25L, "wecomBot:view", "企业微信群机器人查看", "MENU", 0L, 70),
                createPermission(26L, "wecomBot:create", "企业微信群机器人创建", "BUTTON", 25L, 71),
                createPermission(27L, "wecomBot:edit", "企业微信群机器人编辑", "BUTTON", 25L, 72),
                createPermission(28L, "wecomBot:delete", "企业微信群机器人删除", "BUTTON", 25L, 73),
                createPermission(29L, "taskSqlGroup:view", "SQL分组查看", "MENU", 0L, 80),
                createPermission(30L, "taskSqlGroup:create", "SQL分组创建", "BUTTON", 29L, 81),
                createPermission(31L, "taskSqlGroup:edit", "SQL分组编辑", "BUTTON", 29L, 82),
                createPermission(32L, "taskSqlGroup:delete", "SQL分组删除", "BUTTON", 29L, 83),
                createPermission(33L, "notificationRule:view", "通知规则查看", "MENU", 0L, 90),
                createPermission(34L, "notificationRule:create", "通知规则创建", "BUTTON", 33L, 91),
                createPermission(35L, "notificationRule:edit", "通知规则编辑", "BUTTON", 33L, 92),
                createPermission(36L, "notificationRule:delete", "通知规则删除", "BUTTON", 33L, 93),
                createPermission(37L, "wecomIntelligentBot:view", "企业微信智能机器人查看", "MENU", 0L, 100),
                createPermission(38L, "wecomIntelligentBot:create", "企业微信智能机器人创建", "BUTTON", 37L, 101),
                createPermission(39L, "wecomIntelligentBot:edit", "企业微信智能机器人编辑", "BUTTON", 37L, 102),
                createPermission(40L, "wecomIntelligentBot:delete", "企业微信智能机器人删除", "BUTTON", 37L, 103),
                createPermission(41L, "notificationConfig:view", "通知配置查看", "MENU", 0L, 110),
                createPermission(42L, "notificationConfig:create", "通知配置创建", "BUTTON", 41L, 111),
                createPermission(43L, "notificationConfig:edit", "通知配置编辑", "BUTTON", 41L, 112),
                createPermission(44L, "notificationConfig:delete", "通知配置删除", "BUTTON", 41L, 113)
        );
        for (SysPermission permission : permissions) {
            if (sysPermissionMapper.selectById(permission.getId()) == null) {
                sysPermissionMapper.insert(permission);
            }
        }
    }

    private SysPermission createPermission(Long id, String code, String name, String type, Long parentId, int sort) {
        SysPermission p = new SysPermission();
        p.setId(id);
        p.setPermissionCode(code);
        p.setPermissionName(name);
        p.setResourceType(type);
        p.setParentId(parentId);
        p.setSortOrder(sort);
        p.setStatus(1);
        return p;
    }

    private void initAdmin() {
        SysUser admin = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin")
        );
        if (admin == null) {
            admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setNickname("管理员");
            admin.setStatus(1);
            sysUserMapper.insert(admin);

            SysRole adminRole = sysRoleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "ADMIN")
            );
            if (adminRole != null) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(admin.getId());
                userRole.setRoleId(adminRole.getId());
                sysUserRoleMapper.insert(userRole);

                List<SysPermission> permissions = sysPermissionMapper.selectList(null);
                for (SysPermission permission : permissions) {
                    SysRolePermission rp = new SysRolePermission();
                    rp.setRoleId(adminRole.getId());
                    rp.setPermissionId(permission.getId());
                    sysRolePermissionMapper.insert(rp);
                }
            }
        }
    }

    private void ensureAdminHasAllPermissions() {
        SysRole adminRole = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "ADMIN")
        );
        if (adminRole == null) {
            return;
        }
        List<SysPermission> permissions = sysPermissionMapper.selectList(null);
        for (SysPermission permission : permissions) {
            Long permissionId = permission.getId();
            Long count = sysRolePermissionMapper.selectCount(
                    new LambdaQueryWrapper<SysRolePermission>()
                            .eq(SysRolePermission::getRoleId, adminRole.getId())
                            .eq(SysRolePermission::getPermissionId, permissionId)
            );
            if (count == null || count == 0) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(adminRole.getId());
                rp.setPermissionId(permissionId);
                sysRolePermissionMapper.insert(rp);
            }
        }
    }
}
