package com.example.alertservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.alertservice.entity.Message;
import com.example.alertservice.mapper.MessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageMapper messageMapper;

    /**
     * 发送消息
     */
    public Message sendMessage(Long userId, String type, String title, String content, Long relatedId) {
        Message message = new Message();
        message.setUserId(userId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setRelatedId(relatedId);
        message.setIsRead(false);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    /**
     * 获取用户所有消息
     */
    public List<Message> getUserMessages(Long userId) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at");
        return messageMapper.selectList(wrapper);
    }

    /**
     * 获取用户未读消息
     */
    public List<Message> getUnreadMessages(Long userId) {
        return messageMapper.findUnreadByUserId(userId);
    }

    /**
     * 标记单条消息为已读
     */
    public void markAsRead(Long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message != null) {
            message.setIsRead(true);
            messageMapper.updateById(message);
        }
    }

    /**
     * 标记所有消息为已读
     */
    public void markAllAsRead(Long userId) {
        List<Message> messages = getUnreadMessages(userId);
        for (Message message : messages) {
            message.setIsRead(true);
            messageMapper.updateById(message);
        }
    }
}
