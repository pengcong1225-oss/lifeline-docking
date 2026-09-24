package com.lifeline.docking.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 知识库入口跳转。
 *
 * <p>知识库是构建产物（{@code static/kb/index.html}，由 {@code tools/build-kb.mjs} 生成），
 * 这里只负责把 {@code /kb} 与 {@code /kb/} 指过去，方便记忆与分享链接。</p>
 */
@Controller
public class KbController {

    @GetMapping({"/kb", "/kb/"})
    public String index() {
        return "redirect:/kb/index.html";
    }
}
