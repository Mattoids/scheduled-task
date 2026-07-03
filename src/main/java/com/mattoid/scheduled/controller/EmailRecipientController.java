package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.EmailRecipient;
import com.mattoid.scheduled.entity.EmailRecipientGroup;
import com.mattoid.scheduled.service.EmailRecipientGroupService;
import com.mattoid.scheduled.service.EmailRecipientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/email-recipient")
public class EmailRecipientController {

    private final EmailRecipientService emailRecipientService;
    private final EmailRecipientGroupService emailRecipientGroupService;

    public EmailRecipientController(EmailRecipientService emailRecipientService,
                                    EmailRecipientGroupService emailRecipientGroupService) {
        this.emailRecipientService = emailRecipientService;
        this.emailRecipientGroupService = emailRecipientGroupService;
    }

    @PreAuthorize("hasAuthority('email:view')")
    @GetMapping("/page")
    public Result<PageResult<EmailRecipient>> page(PageQuery query, @RequestParam(required = false) Long groupId) {
        LambdaQueryWrapper<EmailRecipient> wrapper = new LambdaQueryWrapper<>();
        if (groupId != null) {
            wrapper.eq(EmailRecipient::getGroupId, groupId);
        }
        Page<EmailRecipient> page = emailRecipientService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('email:view')")
    @GetMapping("/list")
    public Result<List<EmailRecipient>> list() {
        return Result.ok(emailRecipientService.list());
    }

    @PreAuthorize("hasAuthority('email:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody EmailRecipient recipient) {
        return Result.ok(emailRecipientService.save(recipient));
    }

    @PreAuthorize("hasAuthority('email:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody EmailRecipient recipient) {
        recipient.setId(id);
        return Result.ok(emailRecipientService.updateById(recipient));
    }

    @PreAuthorize("hasAuthority('email:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(emailRecipientService.removeById(id));
    }

    @PreAuthorize("hasAuthority('email:view')")
    @GetMapping("/group/list")
    public Result<List<EmailRecipientGroup>> groupList() {
        return Result.ok(emailRecipientGroupService.list());
    }

    @PreAuthorize("hasAuthority('email:create')")
    @PostMapping("/group")
    public Result<Boolean> createGroup(@RequestBody EmailRecipientGroup group) {
        return Result.ok(emailRecipientGroupService.save(group));
    }

    @PreAuthorize("hasAuthority('email:edit')")
    @PutMapping("/group/{id}")
    public Result<Boolean> updateGroup(@PathVariable Long id, @RequestBody EmailRecipientGroup group) {
        group.setId(id);
        return Result.ok(emailRecipientGroupService.updateById(group));
    }

    @PreAuthorize("hasAuthority('email:delete')")
    @DeleteMapping("/group/{id}")
    public Result<Boolean> deleteGroup(@PathVariable Long id) {
        return Result.ok(emailRecipientGroupService.removeById(id));
    }
}
