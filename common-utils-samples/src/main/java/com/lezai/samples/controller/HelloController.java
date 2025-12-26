package com.lezai.samples.controller;

import com.lezai.samples.cache.UserCache;
import com.lezai.samples.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/user")
    public void updateUser(@RequestBody UserCache.User user) {
        userService.updateUser(user);
    }
}
