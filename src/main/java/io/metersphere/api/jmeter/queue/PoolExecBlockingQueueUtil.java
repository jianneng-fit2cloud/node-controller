package io.metersphere.api.jmeter.queue;

import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PoolExecBlockingQueueUtil {
    // 系统级队列控制整体并发数量
    public static Map<String, BlockingQueue<Object>> queue = new ConcurrentHashMap<>();

    private static final String END_SIGN = "RUN-END";
    private static final int QUEUE_SIZE = 1;

    public static void offer(String key) {
        if (StringUtils.isNotEmpty(key) && queue.containsKey(key)) {
            try {
                queue.get(key).offer(END_SIGN);
            } catch (Exception e) {
                LoggerUtil.error(e);
            } finally {
                queue.remove(key);
            }
        }
    }

    public static Object take(String key) {
        try {
            if (StringUtils.isNotEmpty(key) && !queue.containsKey(key)) {
                BlockingQueue<Object> blockingQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);
                queue.put(key, blockingQueue);
                return blockingQueue.poll(30, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            LoggerUtil.error("初始化队列失败：" + e.getMessage());
        }
        return null;
    }

    public static void remove(String key) {
        if (StringUtils.isNotEmpty(key) && queue.containsKey(key)) {
            queue.get(key).offer(END_SIGN);
            queue.remove(key);
        }
    }
}
