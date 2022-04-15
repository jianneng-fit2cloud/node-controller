package io.metersphere.api.jmeter.utils;

import io.metersphere.api.jmeter.queue.ExecThreadPoolExecutor;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import org.apache.commons.lang3.StringUtils;

public class JmeterThreadUtils {
    public static boolean isRunning(String reportId, String testId) {
        if (StringUtils.isEmpty(reportId)) {
            return false;
        }
        if (PoolExecBlockingQueueUtil.queue.containsKey(reportId)) {
            return true;
        }
        if (CommonBeanFactory.getBean(ExecThreadPoolExecutor.class).check(reportId)) {
            return true;
        }
        ThreadGroup currentGroup = Thread.currentThread().getThreadGroup();
        int noThreads = currentGroup.activeCount();
        Thread[] lstThreads = new Thread[noThreads];
        currentGroup.enumerate(lstThreads);
        for (int i = 0; i < noThreads; i++) {
            if (StringUtils.isNotEmpty(reportId) && StringUtils.isNotEmpty(lstThreads[i].getName()) && lstThreads[i].getName().startsWith(reportId)) {
                return true;
            } else if (StringUtils.isNotEmpty(testId) && StringUtils.isNotEmpty(lstThreads[i].getName()) && lstThreads[i].getName().startsWith(testId)) {
                return true;
            }
        }
        return false;
    }

    public static Integer activeCount() {
        ThreadGroup currentGroup = Thread.currentThread().getThreadGroup();
        return currentGroup.activeCount();
    }
}
