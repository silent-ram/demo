package com.example.alertservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.alertservice.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("SELECT * FROM t_message WHERE user_id = #{userId} AND is_read = 0 ORDER BY created_at DESC")
    List<Message> findUnreadByUserId(@Param("userId") Long userId);
}
