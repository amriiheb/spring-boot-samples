package com.wxs.cache.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wxs.cache.entity.User;
import com.wxs.cache.mapper.UserMapper;
import com.wxs.cache.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public void addUser(User user) {
        baseMapper.insert(user);
    }

    @Override
    public User selectById(Integer id) {
        return baseMapper.selectById(id);
    }
}
