package com.example.userservice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.LoginResponse;
import com.example.userservice.dto.UserDTO;
import com.example.userservice.entity.User;
import com.example.userservice.mapper.UserMapper;
import com.example.userservice.config.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        log.debug("开始登录处理: {}", request.getUsername());

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());
        log.debug("执行的SQL: SELECT * FROM t_user WHERE username = '{}'", request.getUsername());

        User user = userMapper.selectOne(wrapper);
        log.debug("查询到的用户: {}", user);

        if (user == null) {
            log.warn("登录失败 - 用户不存在: {}", request.getUsername());
            throw new RuntimeException("用户不存在");
        }

        log.debug("数据库中的密码哈希: {}", user.getPassword());
        log.debug("输入的密码: {}", request.getPassword());

        boolean matches = passwordEncoder.matches(request.getPassword(), user.getPassword());
        log.debug("密码匹配结果: {}", matches);

        if (!matches) {
            log.warn("登录失败 - 密码错误: username={}", request.getUsername());
            throw new RuntimeException("密码错误");
        }

        log.info("登录成功: username={}, role={}", user.getUsername(), user.getRole());

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        UserDTO userDTO = new UserDTO(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());

        return new LoginResponse(token, userDTO);
    }

    public void register(LoginRequest request) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());
        User existUser = userMapper.selectOne(wrapper);

        if (existUser != null) {
            log.warn("注册失败 - 用户名已存在: {}", request.getUsername());
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("OPERATOR");

        userMapper.insert(user);
        log.info("用户注册成功: username={}", user.getUsername());
    }

    public UserDTO getUserInfo(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            log.warn("获取用户信息失败 - 用户不存在: {}", username);
            throw new RuntimeException("用户不存在");
        }

        return new UserDTO(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }

    public List<UserDTO> listUsers() {
        List<User> users = userMapper.selectList(null);
        log.info("获取用户列表，共{}个用户", users.size());
        return users.stream()
                .map(user -> new UserDTO(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt()))
                .toList();
    }

    public void updateUser(Long id, UserDTO userDTO) {
        User user = userMapper.selectById(id);
        if (user == null) {
            log.warn("更新用户失败 - 用户不存在: id={}", id);
            throw new RuntimeException("用户不存在");
        }

        if (userDTO.getUsername() != null && !userDTO.getUsername().equals(user.getUsername())) {
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("username", userDTO.getUsername());
            User existUser = userMapper.selectOne(wrapper);
            if (existUser != null) {
                log.warn("更新用户失败 - 用户名已存在: username={}", userDTO.getUsername());
                throw new RuntimeException("用户名已存在");
            }
            user.setUsername(userDTO.getUsername());
        }

        if (userDTO.getRole() != null) {
            user.setRole(userDTO.getRole());
        }

        userMapper.updateById(user);
        log.info("用户信息更新成功: id={}, username={}", id, user.getUsername());
    }

    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            log.warn("删除用户失败 - 用户不存在: id={}", id);
            throw new RuntimeException("用户不存在");
        }

        if ("ADMIN".equals(user.getRole())) {
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("role", "ADMIN");
            Long adminCount = userMapper.selectCount(wrapper);
            if (adminCount <= 1) {
                log.warn("删除用户失败 - 不能删除最后一个管理员账户: id={}", id);
                throw new RuntimeException("不能删除最后一个管理员账户");
            }
        }

        userMapper.deleteById(id);
        log.info("用户删除成功: id={}, username={}", id, user.getUsername());
    }
}