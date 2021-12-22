package io.metersphere.api.jmeter;

import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.service.ProducerService;
import io.metersphere.dto.ResultDTO;
import io.metersphere.jmeter.MsExecListener;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class APISingleResultListener extends MsExecListener {
    private PrintStream oldPrintStream = System.out;

    private static ByteArrayOutputStream bos = new ByteArrayOutputStream();

    public static void setConsole() {
        System.setOut(new PrintStream(bos));
    }

    private String getConsole() {
        System.setOut(oldPrintStream);
        return bos.toString();
    }

    @Override
    public void handleTeardownTest(ResultDTO dto, Map<String, Object> kafkaConfig) {
        LoggerUtil.info("开始处理单条执行结果报告【" + dto.getReportId() + " 】,资源【 " + dto.getTestId() + " 】");
        dto.setConsole(getConsole());
        CommonBeanFactory.getBean(ProducerService.class).send(dto, kafkaConfig);
    }

    @Override
    public void testEnded(ResultDTO dto, Map<String, Object> kafkaConfig) {
        PoolExecBlockingQueueUtil.offer(dto.getReportId());
        LoggerUtil.info("报告【" + dto.getReportId() + " 】执行完成");
        if (StringUtils.isNotEmpty(dto.getReportId())) {
            BlockingQueueUtil.remove(dto.getReportId());
        }
        dto.setConsole(getConsole());
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
    }
}
