package com.demo.upi_offline_mesh.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/{name}")
    public String getUser(@PathVariable String name) {
        return "Hello " + name;
    }
}