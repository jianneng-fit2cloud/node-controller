package io.metersphere.api.controller;

import io.metersphere.api.jmeter.JmeterLoggerAppender;
import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.utils.JmeterThreadUtils;
import io.metersphere.api.module.JvmInfo;
import io.metersphere.api.service.JmeterExecuteService;
import io.metersphere.api.service.JvmService;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.jmeter.LocalRunner;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/jmeter")
public class JmeterExecuteController {

    @Resource
    private JmeterExecuteService jmeterExecuteService;

    @PostMapping(value = "/api/start")
    public String apiStartRun(@RequestBody JmeterRunRequestDTO runRequest) {
        if (StringUtils.equals(runRequest.getReportType(), RunModeConstants.SET_REPORT.toString())) {
            return jmeterExecuteService.runStart(runRequest);
        } else if (BlockingQueueUtil.add(runRequest.getReportId())) {
            return jmeterExecuteService.runStart(runRequest);
        }
        return "当前报告 " + runRequest.getReportId() + " 正在执行中";
    }

    @GetMapping("/get/running/queue/{reportId}")
    public boolean getRunningQueue(@PathVariable String reportId) {
        return JmeterThreadUtils.isRunning(reportId, null);
    }

    @GetMapping("/status")
    public String getStatus() {
        return "OK";
    }

    @GetMapping("/getJvmInfo")
    public JvmInfo getJvmInfo() {
        return JvmService.jvmInfo();
    }

    @GetMapping("/stop")
    public void stop(@RequestBody List<String> keys) {
        new LocalRunner().stop(keys);
    }

    @GetMapping("/log/debug/{enable}")
    public boolean debug(@PathVariable boolean enable) {
        return JmeterLoggerAppender.enable = enable;
    }
}
