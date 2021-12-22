package io.metersphere.api.jmeter.queue;

import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.service.ProducerService;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.dto.ResultDTO;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import java.util.HashMap;

public class SystemExecTask implements Runnable {
    private JmeterRunRequestDTO request;

    public SystemExecTask(JmeterRunRequestDTO request) {
        this.request = request;
    }

    public JmeterRunRequestDTO getRequest() {
        return this.request;
    }

    @Override
    public void run() {
        LoggerUtil.info("开始执行报告ID：【 " + request.getReportId() + " 】,资源ID【 " + request.getTestId() + " 】");
        JMeterService jMeterService = CommonBeanFactory.getBean(JMeterService.class);
        jMeterService.addQueue(request);
        if (StringUtils.isNotEmpty(request.getReportId())) {
            Object res = PoolExecBlockingQueueUtil.take(request.getReportId());
            if (res == null) {
                LoggerUtil.info("执行报告：【 " + request.getReportId() + " 】,资源ID【 " + request.getTestId() + " 】执行超时");
                ResultDTO dto = new ResultDTO();
                BeanUtils.copyProperties(dto, request);
                if (dto.getArbitraryData() == null || dto.getArbitraryData().isEmpty()) {
                    dto.setArbitraryData(new HashMap<String, Object>() {{
                        this.put("TEST_END", true);
                        this.put("TIMEOUT", true);
                    }});
                } else {
                    dto.getArbitraryData().put("TEST_END", true);
                    dto.getArbitraryData().put("TIMEOUT", true);
                }
                CommonBeanFactory.getBean(ProducerService.class).send(dto, request.getKafkaConfig());
            }
        }
        LoggerUtil.info("任务：【 " + request.getReportId() + " 】执行完成");
    }
}
