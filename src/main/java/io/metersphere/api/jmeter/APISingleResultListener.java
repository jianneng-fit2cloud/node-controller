package io.metersphere.api.jmeter;

import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.FixedCapacityUtils;
import io.metersphere.api.service.JvmService;
import io.metersphere.api.service.ProducerService;
import io.metersphere.cache.JMeterEngineCache;
import io.metersphere.dto.ResultDTO;
import io.metersphere.jmeter.MsExecListener;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class APISingleResultListener extends MsExecListener {

    public static String getJmeterLogger(String testId) {
        try {
            Long startTime = FixedCapacityUtils.jmeterLogTask.get(testId);
            if (startTime == null) {
                startTime = FixedCapacityUtils.jmeterLogTask.get("[" + testId + "]");
            }
            if (startTime == null) {
                startTime = System.currentTimeMillis();
            }
            Long endTime = System.currentTimeMillis();
            Long finalStartTime = startTime;
            String logMessage = FixedCapacityUtils.fixedCapacityCache.entrySet().stream()
                    .filter(map -> map.getKey() > finalStartTime && map.getKey() < endTime)
                    .map(map -> map.getValue()).collect(Collectors.joining());
            return logMessage;
        } catch (Exception e) {
            return "";
        } finally {
            if (FixedCapacityUtils.jmeterLogTask.containsKey(testId)) {
                FixedCapacityUtils.jmeterLogTask.remove(testId);
            }
        }
    }

    @Override
    public void handleTeardownTest(ResultDTO dto, Map<String, Object> kafkaConfig) {
        LoggerUtil.info("开始处理单条执行结果报告【" + dto.getReportId() + " 】,资源【 " + dto.getTestId() + " 】");
        dto.setConsole(getJmeterLogger(dto.getReportId()));
        CommonBeanFactory.getBean(ProducerService.class).send(dto, kafkaConfig);
    }

    @Override
    public void testEnded(ResultDTO dto, Map<String, Object> kafkaConfig) {
        if (JMeterEngineCache.runningEngine.containsKey(dto.getReportId())) {
            JMeterEngineCache.runningEngine.remove(dto.getReportId());
        }
        PoolExecBlockingQueueUtil.offer(dto.getReportId());
        LoggerUtil.info("报告【" + dto.getReportId() + " 】执行完成");
        if (StringUtils.isNotEmpty(dto.getReportId())) {
            BlockingQueueUtil.remove(dto.getReportId());
        }
        dto.setConsole(getJmeterLogger(dto.getReportId()));
        if (dto.getArbitraryData() == null || dto.getArbitraryData().isEmpty()) {
            dto.setArbitraryData(new HashMap<String, Object>() {{
                this.put("TEST_END", true);
            }});
        } else {
            dto.getArbitraryData().put("TEST_END", true);
        }
        FileUtils.deleteFile(FileUtils.BODY_FILE_DIR + "/" + dto.getReportId() + "_" + dto.getTestId() + ".jmx");
        // 存储结果
        CommonBeanFactory.getBean(ProducerService.class).send(dto, kafkaConfig);
        LoggerUtil.info(JvmService.jvmInfo().toString());
    }
}
