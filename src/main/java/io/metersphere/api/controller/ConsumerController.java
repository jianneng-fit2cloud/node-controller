package io.metersphere.api.controller;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("consumer")
public class ConsumerController {

    /**
     * 创建消费者分组
     */
    @PostMapping("/create")
    public void create(@RequestBody Map<String, Object> consumerProps) {

    }

    /**
     * 停止所有消费监听
     */
    @GetMapping("/stop")
    public void stop() {

    }
}
