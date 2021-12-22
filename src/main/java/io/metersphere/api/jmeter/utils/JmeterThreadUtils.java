package io.metersphere.api.jmeter.utils;

import org.apache.commons.lang3.StringUtils;

public class JmeterThreadUtils {
    public static boolean isRunning(String reportId, String testId) {
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
}
