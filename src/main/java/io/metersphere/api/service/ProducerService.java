package io.metersphere.api.service;

import com.alibaba.fastjson.JSON;
import io.metersphere.api.config.KafkaConfig;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.ResultDTO;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProducerService {
    // 初始化不同地址kafka,每个地址初始化一个线程
    private Map<String, KafkaTemplate> kafkaTemplateMap = new ConcurrentHashMap<>();

    public KafkaTemplate init(Map<String, Object> producerProps) {
        try {
            Object serverUrl = producerProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);
            if (serverUrl != null && kafkaTemplateMap.containsKey(serverUrl.toString())) {
                return kafkaTemplateMap.get(serverUrl);
            } else {
                producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                        "org.apache.kafka.common.serialization.StringSerializer");
                producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        "org.apache.kafka.common.serialization.StringSerializer");
                DefaultKafkaProducerFactory pf = new DefaultKafkaProducerFactory<>(producerProps);
                KafkaTemplate kafkaTemplate = new KafkaTemplate(pf, true);
                kafkaTemplateMap.put(serverUrl.toString(), kafkaTemplate);
                return kafkaTemplate;
            }
        } catch (Exception e) {
            LoggerUtil.error(e);
            return null;
        }
    }

    public void send(String message, Map<String, Object> producerProps) {
        KafkaTemplate kafkaTemplate = this.init(producerProps);
        if (kafkaTemplate != null) {
            kafkaTemplate.send(KafkaConfig.TOPICS, message);
            kafkaTemplate.flush();
        }
    }

    public void send(ResultDTO dto, Map<String, Object> kafkaConfig) {
        ProducerService producerServer = CommonBeanFactory.getBean(ProducerService.class);
        try {
            if (producerServer != null) {
                LoggerUtil.info("执行完成开始同步发送KAFKA【" + dto.getTestId() + "】");
                producerServer.send(JSON.toJSONString(dto), kafkaConfig);
                LoggerUtil.info("同步发送报告信息到KAFKA完成【" + dto.getTestId() + "】");
            }
        } catch (Exception ex) {
            LoggerUtil.error("KAFKA 推送结果异常：[" + dto.getTestId() + "]" + ex.getMessage());
            // 补偿一个结果防止持续Running
            if (dto != null && dto.getRequestResults().size() > 0) {
                dto.getRequestResults().clear();
            }
            producerServer.send(JSON.toJSONString(dto), kafkaConfig);
        }

        if (dto != null && StringUtils.equals(dto.getReportType(), RunModeConstants.SET_REPORT.name())) {
            LoggerUtil.info("处理接口集合报告ID：" + dto.getReportId());
            JmeterExecuteService jmeterExecuteService = CommonBeanFactory.getBean(JmeterExecuteService.class);
            if (jmeterExecuteService != null) {
                jmeterExecuteService.remove(dto.getReportId(), dto.getTestId());
                LoggerUtil.info("正在执行中的并发报告数量：" + jmeterExecuteService.getRunningSize());
                LoggerUtil.info("正在执行中的场景[" + dto.getReportId() + "]的数量：" + jmeterExecuteService.getRunningTasks(dto.getReportId()));
                LoggerUtil.info("正在执行中的场景[" + dto.getReportId() + "]的内容：" + jmeterExecuteService.getRunningList(dto.getReportId()));
            }
        }
    }
}
