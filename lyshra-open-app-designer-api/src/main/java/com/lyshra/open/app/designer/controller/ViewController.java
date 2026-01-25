package com.lyshra.open.app.designer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * View controller for Thymeleaf templates.
 */
@Controller
public class ViewController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "login";
    }

    @GetMapping("/designer")
    public String designer() {
        return "designer";
    }

    @GetMapping("/workflows")
    public String workflows() {
        return "workflows";
    }

    @GetMapping("/executions")
    public String executions() {
        return "executions";
    }

    @GetMapping("/monitoring")
    public String monitoring() {
        return "monitoring";
    }

    @GetMapping("/profile")
    public String profile() {
        return "index";
    }
}
