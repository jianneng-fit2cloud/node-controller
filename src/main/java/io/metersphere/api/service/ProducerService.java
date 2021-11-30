package io.metersphere.api.service;

import io.metersphere.api.config.KafkaConfig;
import io.metersphere.node.util.LogUtil;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ProducerService {

    public KafkaTemplate init(Map<String, Object> producerProps) {
        try {
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                    "org.apache.kafka.common.serialization.StringSerializer");
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    "org.apache.kafka.common.serialization.StringSerializer");
            DefaultKafkaProducerFactory pf = new DefaultKafkaProducerFactory<>(producerProps);
            KafkaTemplate kafkaTemplate = new KafkaTemplate(pf, true);
            return kafkaTemplate;
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
