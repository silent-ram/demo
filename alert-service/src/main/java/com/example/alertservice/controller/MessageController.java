package com.example.alertservice.controller;

import com.example.alertservice.entity.Message;
import com.example.alertservice.exception.Result;
import com.example.alertservice.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/message")
@Tag(name = "消息管理", description = "消息CRUD操作接口")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @GetMapping("/list")
    @Operation(summary = "获取消息列表", description = "获取指定用户的所有消息")
    public Result<List<Message>> getUserMessages(@RequestParam Long userId) {
        List<Message> messages = messageService.getUserMessages(userId);
        return Result.success(messages);
    }

    @GetMapping("/unread")
    @Operation(summary = "获取未读消息", description = "获取指定用户的未读消息")
    public Result<List<Message>> getUnreadMessages(@RequestParam Long userId) {
        List<Message> messages = messageService.getUnreadMessages(userId);
        return Result.success(messages);
    }

    @PostMapping("/read/{id}")
    @Operation(summary = "标记已读", description = "标记单条消息为已读")
    public Result<String> markAsRead(@PathVariable Long id) {
        messageService.markAsRead(id);
        return Result.success("标记已读成功", null);
    }

    @PostMapping("/read-all")
    @Operation(summary = "全部标记已读", description = "标记用户所有消息为已读")
    public Result<String> markAllAsRead(@RequestParam Long userId) {
        messageService.markAllAsRead(userId);
        return Result.success("全部标记已读成功", null);
    }
}
