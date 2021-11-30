package io.metersphere.api.controller;

import io.metersphere.api.service.ProducerService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("producer")
public class ProducerController {
    @Resource
    private ProducerService producerService;

    @PostMapping("/create")
    public String create(@RequestBody Map<String, Object> producerProps) {
        producerService.init(producerProps);
        return "SUCCESS";
    }
}

