package com.irondust.search.admin;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/admin-ui")
public class AdminUiController {
    @GetMapping(value = "", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() throws IOException {
        ClassPathResource res = new ClassPathResource("static/admin/index.html");
        byte[] bytes = res.getContentAsByteArray();
        String html = new String(bytes, StandardCharsets.UTF_8);
        return ResponseEntity.ok(html);
    }
}


