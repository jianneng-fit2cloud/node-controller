package io.metersphere.api.controller.request;

import io.metersphere.api.jmeter.utils.RunModeConfig;
import lombok.Data;

import java.util.Map;

@Data
public class RunRequest {
    private String testId;
    // api / case 或有这个属性值
    private String reportId;
    // 开启定时同步
    private boolean enable;
    private String url;
    private String userId;
    private boolean isDebug;
    private String runMode;
    private String jmx;
    // 集成报告ID
    private String amassReport;
    private RunModeConfig config;

    private Map<String, Object> kafka;

}
