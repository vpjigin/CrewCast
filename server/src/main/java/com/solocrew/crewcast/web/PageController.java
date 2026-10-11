package com.solocrew.crewcast.web;

import com.solocrew.crewcast.share.ShareRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Controller
public class PageController {

    private final ShareRegistry shareRegistry;

    public PageController(ShareRegistry shareRegistry) {
        this.shareRegistry = shareRegistry;
    }

    @GetMapping("/watch/{id}")
    public String watchPage(@PathVariable String id) throws IOException {
        if (shareRegistry.findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/player/watch.html";
    }
}
