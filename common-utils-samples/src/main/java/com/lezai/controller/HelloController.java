package com.lezai.controller;

import com.lezai.cache.UserCache;
import com.lezai.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hello")
@RequiredArgsConstructor
public class HelloController {

    private final UserService userService;
    @GetMapping
    public String hello() {
        return "hello world";
    }

    @GetMapping("/user")
    public UserCache.User getUser(@RequestParam("userId") String userId) {
        return userService.getUserById(userId);
    }
}
