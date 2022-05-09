package io.metersphere.api.jmeter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.metersphere.api.jmeter.utils.DateUtils;
import io.metersphere.api.jmeter.utils.FixedCapacityUtils;
import io.metersphere.utils.LoggerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmeterLoggerAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    public static Logger logger = LoggerFactory.getLogger(getLogClass());

    private static String getLogClass() {
        String str = "";
        StackTraceElement[] stack = (new Throwable()).getStackTrace();
        if (stack.length > 3) {
            StackTraceElement ste = stack[3];
            str = ste.getClassName();// 类名称
        }

        return str;
    }

    @Override
    public void append(ILoggingEvent event) {
        try {
            if (!event.getLevel().levelStr.equals(LoggerUtil.DEBUG)) {
                StringBuffer message = new StringBuffer();
                message.append(DateUtils.getTimeStr(event.getTimeStamp())).append(" ")
                        .append(event.getLevel()).append(" ")
                        .append(event.getThreadName()).append(" ")
                        .append(event.getFormattedMessage()).append("\n");

                if (event.getThrowableProxy() != null) {
                    message.append(event.getThrowableProxy().getMessage()).append("\n");
                    message.append(event.getThrowableProxy().getClassName()).append("\n");
                    if (event.getThrowableProxy().getStackTraceElementProxyArray() != null) {
                        for (StackTraceElementProxy stackTraceElementProxy : event.getThrowableProxy().getStackTraceElementProxyArray()) {
                            message.append("   ").append(stackTraceElementProxy.getSTEAsString()).append("\n");
                        }
                    }
                }
                if (message != null && !message.toString().contains("java.net.UnknownHostException")) {
                    if (FixedCapacityUtils.fixedCapacityCache.containsKey(event.getTimeStamp())) {
                        FixedCapacityUtils.fixedCapacityCache.get(event.getTimeStamp()).append(message);
                    } else {
                        FixedCapacityUtils.fixedCapacityCache.put(event.getTimeStamp(), message);
                    }
                }
                logger.info("JMETER-LOG" + message.toString());
            }
        } catch (Exception e) {
            LoggerUtil.error(e);
        }
    }
}