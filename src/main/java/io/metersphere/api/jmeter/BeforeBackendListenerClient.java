package io.metersphere.api.jmeter;

import com.alibaba.fastjson.JSON;
import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.JmeterThreadUtils;
import io.metersphere.api.service.JvmService;
import io.metersphere.api.service.ProducerService;
import io.metersphere.cache.JMeterEngineCache;
import io.metersphere.constants.BackendListenerConstants;
import io.metersphere.dto.RequestResult;
import io.metersphere.dto.ResultDTO;
import io.metersphere.jmeter.JMeterBase;
import io.metersphere.utils.ListenerUtil;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;

import java.io.Serializable;
import java.util.*;

/**
 * JMeter BackendListener扩展, jmx脚本中使用
 */
public class BeforeBackendListenerClient extends AbstractBackendListenerClient implements Serializable {
    private String runMode = BackendListenerConstants.RUN.name();
    private String listenerClazz;

    // KAFKA 配置信息
    private Map<String, Object> producerProps;
    private ResultDTO dto;
    private List<SampleResult> queues;

    @Override
    public void setupTest(BackendListenerContext context) throws Exception {
        this.setParam(context);
        LoggerUtil.info("TestStarted接收到参数：报告【" + JSON.toJSONString(dto) + " 】");
        LoggerUtil.info("TestStarted接收到参数：KAFKA【" + JSON.toJSONString(producerProps) + " 】");
        LoggerUtil.info("TestStarted接收到参数：处理类【" + listenerClazz + " 】");
        super.setupTest(context);
    }


    @Override
    public void handleSampleResults(List<SampleResult> sampleResults, BackendListenerContext context) {
        LoggerUtil.info("实施接收到数据【" + sampleResults.size() + " 】, " + dto.getQueueId());
        queues.addAll(sampleResults);
    }


    @Override
    public void teardownTest(BackendListenerContext context) {
        try {
            super.teardownTest(context);

            // 处理结果集
            this.format();

            if (JMeterEngineCache.runningEngine.containsKey(dto.getReportId())) {
                JMeterEngineCache.runningEngine.remove(dto.getReportId());
            }
            LoggerUtil.info("JMETER-测试报告【" + dto.getReportId() + "】资源【 " + dto.getTestId() + " 】执行结束");

            PoolExecBlockingQueueUtil.offer(dto.getReportId());
            if (StringUtils.isNotEmpty(dto.getReportId())) {
                BlockingQueueUtil.remove(dto.getReportId());
            }
            dto.setConsole(APISingleResultListener.getJmeterLogger(dto.getReportId()));

            if (dto.getArbitraryData() == null || dto.getArbitraryData().isEmpty()) {
                dto.setArbitraryData(new HashMap<String, Object>() {{
                    this.put("TEST_END", true);
                }});
            } else {
                dto.getArbitraryData().put("TEST_END", true);
            }
            FileUtils.deleteFile(FileUtils.BODY_FILE_DIR + "/" + dto.getReportId() + "_" + dto.getTestId() + ".jmx");

            LoggerUtil.info(" 系统当线程总数==========>>>>>>：" + JmeterThreadUtils.threadCount());
            LoggerUtil.info(JvmService.jvmInfo().toString());

            // 发送整体执行完成标示
            CommonBeanFactory.getBean(ProducerService.class).send(dto, producerProps);

            LoggerUtil.info("报告【" + dto.getReportId() + " 】执行完成");
        } catch (Exception e) {
            LoggerUtil.error("JMETER-测试报告【" + dto.getReportId() + "】资源【 " + dto.getTestId() + " 】执行异常", e);
        }
    }

    private void format() {
        try {
            LoggerUtil.info("开始处理结果集报告【" + dto.getReportId() + " 】,资源【 " + dto.getTestId() + " 】");

            List<RequestResult> requestResults = new LinkedList<>();
            List<String> environmentList = new ArrayList<>();
            queues.forEach(result -> {
                ListenerUtil.setVars(result);
                RequestResult requestResult = JMeterBase.getRequestResult(result);
                if (StringUtils.equals(result.getSampleLabel(), ListenerUtil.RUNNING_DEBUG_SAMPLER_NAME)) {
                    String evnStr = result.getResponseDataAsString();
                    environmentList.add(evnStr);
                } else {
                    boolean resultNotFilterOut = ListenerUtil.checkResultIsNotFilterOut(requestResult);
                    if (resultNotFilterOut) {
                        if (StringUtils.isNotEmpty(requestResult.getName()) && requestResult.getName().startsWith("Transaction=")) {
                            requestResults.addAll(requestResult.getSubRequestResults());
                        } else {
                            requestResults.add(requestResult);
                        }
                    }
                }
            });
            dto.setRequestResults(requestResults);
            ListenerUtil.setEev(dto, environmentList);
            LoggerUtil.info("完成处理结果集报告【" + dto.getReportId() + " 】,资源【 " + dto.getTestId() + " 】");
            queues.clear();
        } catch (Exception e) {
            LoggerUtil.error("JMETER-调用存储方法失败：" + e.getMessage());
        }
    }

    private void setParam(BackendListenerContext context) {
        dto = new ResultDTO();
        dto.setTestId(context.getParameter(BackendListenerConstants.TEST_ID.name()));
        dto.setRunMode(context.getParameter(BackendListenerConstants.RUN_MODE.name()));
        dto.setReportId(context.getParameter(BackendListenerConstants.REPORT_ID.name()));
        dto.setReportType(context.getParameter(BackendListenerConstants.REPORT_TYPE.name()));
        dto.setTestPlanReportId(context.getParameter(BackendListenerConstants.MS_TEST_PLAN_REPORT_ID.name()));
        this.producerProps = new HashMap<>();
        if (StringUtils.isNotEmpty(context.getParameter(BackendListenerConstants.KAFKA_CONFIG.name()))) {
            this.producerProps = JSON.parseObject(context.getParameter(BackendListenerConstants.KAFKA_CONFIG.name()), Map.class);
        }
        this.listenerClazz = context.getParameter(BackendListenerConstants.CLASS_NAME.name());

        dto.setQueueId(context.getParameter(BackendListenerConstants.QUEUE_ID.name()));
        dto.setRunType(context.getParameter(BackendListenerConstants.RUN_TYPE.name()));

        String ept = context.getParameter(BackendListenerConstants.EPT.name());
        if (StringUtils.isNotEmpty(ept)) {
            dto.setExtendedParameters(JSON.parseObject(context.getParameter(BackendListenerConstants.EPT.name()), Map.class));
        }
        queues = new LinkedList<>();
    }
}
