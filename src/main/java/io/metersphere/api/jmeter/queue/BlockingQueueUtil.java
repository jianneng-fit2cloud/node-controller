package io.metersphere.api.jmeter.queue;

import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlockingQueueUtil {
    final static List<String> queue = Collections.synchronizedList(new ArrayList());

    public static boolean add(String key) {
        if (StringUtils.isNotEmpty(key) && !queue.contains(key)) {
            try {
                queue.add(key);
                LoggerUtil.info("执行任务入列：" + key + " 剩余：" + queue.size());
                return true;
            } catch (Exception e) {
                LoggerUtil.error(e);
            }
        }
        return false;
    }

    public static void remove(String key) {
        try {
            if (StringUtils.isNotEmpty(key)) {
                LoggerUtil.info("执行任务出列：" + key);
                queue.remove(key);
            }
        } catch (Exception e) {
            LoggerUtil.error("获取队列失败：" + e.getMessage());
        }
    }
}
