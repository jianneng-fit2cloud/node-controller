package io.metersphere.api.jmeter;

import com.alibaba.fastjson.JSON;
import io.metersphere.api.jmeter.queue.ExecThreadPoolExecutor;
import io.metersphere.api.jmeter.utils.FixedCapacityUtils;
import io.metersphere.api.jmeter.utils.JmeterProperties;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.constants.BackendListenerConstants;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.jmeter.LocalRunner;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections4.MapUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.visualizers.backend.BackendListener;
import org.apache.jorphan.collections.HashTree;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;

@Service
public class JMeterService {

    @Resource
    private JmeterProperties jmeterProperties;
    @Resource
    private ExecThreadPoolExecutor execThreadPoolExecutor;

    @PostConstruct
    public void init() {
        String JMETER_HOME = getJmeterHome();
        String JMETER_PROPERTIES = JMETER_HOME + "/bin/jmeter.properties";
        JMeterUtils.loadJMeterProperties(JMETER_PROPERTIES);
        JMeterUtils.setJMeterHome(JMETER_HOME);
        JMeterUtils.setLocale(LocaleContextHolder.getLocale());
    }

    public String getJmeterHome() {
        String home = getClass().getResource("/").getPath() + "jmeter";
        try {
            File file = new File(home);
            if (file.exists()) {
                return home;
            } else {
                return jmeterProperties.getHome();
            }
        } catch (Exception e) {
            return jmeterProperties.getHome();
        }
    }
    public static void addBackendListener(JmeterRunRequestDTO request, HashTree hashTree) {
        LoggerUtil.debug("开始为报告【 " + request.getReportId() + "】，资源【" + request.getTestId() + "】添加BackendListener");
        BackendListener backendListener = new BackendListener();
        backendListener.setName(request.getReportId() + "_" + request.getTestId());
        Arguments arguments = new Arguments();
        arguments.addArgument(BackendListenerConstants.NAME.name(), request.getReportId() + "_" + request.getTestId());
        arguments.addArgument(BackendListenerConstants.REPORT_ID.name(), request.getReportId());
        arguments.addArgument(BackendListenerConstants.TEST_ID.name(), request.getTestId());
        arguments.addArgument(BackendListenerConstants.RUN_MODE.name(), request.getRunMode());
        arguments.addArgument(BackendListenerConstants.REPORT_TYPE.name(), request.getReportType());
        arguments.addArgument(BackendListenerConstants.MS_TEST_PLAN_REPORT_ID.name(), request.getTestPlanReportId());
        arguments.addArgument(BackendListenerConstants.QUEUE_ID.name(), request.getQueueId());
        arguments.addArgument(BackendListenerConstants.RUN_TYPE.name(), request.getRunType());
        if (MapUtils.isNotEmpty(request.getExtendedParameters())) {
            arguments.addArgument(BackendListenerConstants.EPT.name(), JSON.toJSONString(request.getExtendedParameters()));
        }

        if (request.getKafkaConfig() != null && request.getKafkaConfig().size() > 0) {
            arguments.addArgument(BackendListenerConstants.KAFKA_CONFIG.name(), JSON.toJSONString(request.getKafkaConfig()));
        }

        backendListener.setArguments(arguments);
        backendListener.setClassname(BeforeBackendListenerClient.class.getCanonicalName());
        if (hashTree != null) {
            hashTree.add(hashTree.getArray()[0], backendListener);
        }

        LoggerUtil.debug("开始为报告【 " + request.getReportId() + "】，资源【" + request.getTestId() + "】添加BackendListener 结束");
    }

    public void runLocal(JmeterRunRequestDTO runRequest, HashTree testPlan) {
        try {
            init();
            if (!FixedCapacityUtils.jmeterLogTask.containsKey(runRequest.getReportId())) {
                FixedCapacityUtils.jmeterLogTask.put(runRequest.getReportId(), System.currentTimeMillis());
            }
            runRequest.setHashTree(testPlan);
            this.addBackendListener(runRequest, runRequest.getHashTree());
            LocalRunner runner = new LocalRunner(testPlan);
            runner.run(runRequest.getReportId());
        } catch (Exception e) {
            LoggerUtil.error(e.getMessage(), e);
            MSException.throwException("读取脚本失败");
        }
    }

    public void run(JmeterRunRequestDTO request) {
        if (request.getCorePoolSize() > 0) {
            execThreadPoolExecutor.setCorePoolSize(request.getCorePoolSize());
        }
        execThreadPoolExecutor.addTask(request);
    }

    public void addQueue(JmeterRunRequestDTO request) {
        this.runLocal(request, request.getHashTree());
    }
}
