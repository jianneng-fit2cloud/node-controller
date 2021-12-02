package io.metersphere.api.service;

import io.metersphere.api.config.KafkaConfig;
import io.metersphere.node.util.LogUtil;
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
            LogUtil.error(e);
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
}
