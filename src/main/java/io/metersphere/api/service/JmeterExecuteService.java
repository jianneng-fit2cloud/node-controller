package io.metersphere.api.service;

import com.alibaba.fastjson.JSON;
import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.NewDriver;
import org.apache.jmeter.save.SaveService;
import org.apache.jorphan.collections.HashTree;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JmeterExecuteService {
    @Resource
    private JMeterService jMeterService;

    private static String url = null;
    private static String plugUrl = null;

    // 记录所以执行中的请求/场景
    private Map<String, List<String>> runningTasks = new HashMap<>();

    public String runStart(JmeterRunRequestDTO runRequest) {
        try {
            if (runRequest != null && StringUtils.equals(runRequest.getReportType(), RunModeConstants.SET_REPORT.name())) {
                this.putRunningTasks(runRequest.getReportId(), runRequest.getTestId());
            }
            if (runRequest.getKafkaConfig() == null) {
                return "KAFKA 初始化失败，请检查配置";
            }
            // 生成附件/JAR文件
            URL urlObject = new URL(runRequest.getPlatformUrl());
            String jarUrl = urlObject.getProtocol() + "://" + urlObject.getHost() + (urlObject.getPort() > 0 ? ":" + urlObject.getPort() : "") + "/api/jmeter/download/jar";
            String plugJarUrl = urlObject.getProtocol() + "://" + urlObject.getHost() + (urlObject.getPort() > 0 ? ":" + urlObject.getPort() : "") + "/api/jmeter/download/plug/jar";

            if (StringUtils.isEmpty(url)) {
                LoggerUtil.info("开始同步上传的JAR：" + jarUrl);
                File file = ZipSpider.downloadFile(jarUrl, FileUtils.JAR_FILE_DIR);
                if (file != null) {
                    ZipSpider.unzip(file.getPath(), FileUtils.JAR_FILE_DIR);
                    this.loadJar(FileUtils.JAR_FILE_DIR);
                }
            }
            if (StringUtils.isEmpty(plugUrl)) {
                LoggerUtil.info("开始同步插件JAR：" + plugJarUrl);
                File plugFile = ZipSpider.downloadFile(plugJarUrl, FileUtils.JAR_PLUG_FILE_DIR);
                if (plugFile != null) {
                    ZipSpider.unzip(plugFile.getPath(), FileUtils.JAR_PLUG_FILE_DIR);
                    this.loadPlugJar(FileUtils.JAR_PLUG_FILE_DIR);
                }
            }
            url = jarUrl;
            plugUrl = plugJarUrl;
            LoggerUtil.info("开始拉取脚本和脚本附件：" + runRequest.getPlatformUrl());

            File bodyFile = ZipSpider.downloadFile(runRequest.getPlatformUrl(), FileUtils.BODY_FILE_DIR);
            if (bodyFile != null) {
                ZipSpider.unzip(bodyFile.getPath(), FileUtils.BODY_FILE_DIR);
                File jmxFile = new File(FileUtils.BODY_FILE_DIR + "/" + runRequest.getReportId() + "_" + runRequest.getTestId() + ".jmx");
                // 生成执行脚本
                HashTree testPlan = SaveService.loadTree(jmxFile);
                // 开始执行
                runRequest.setHashTree(testPlan);
                jMeterService.run(runRequest);
                FileUtils.deleteFile(bodyFile.getPath());
            } else {
                MSException.throwException("未找到执行的JMX文件");
            }
        } catch (Exception e) {
            LoggerUtil.error(e.getMessage());
            return e.getMessage();
        }
        return "SUCCESS";
    }

    private void loadJar(String path) {
        try {
            NewDriver.addPath(path);
        } catch (MalformedURLException e) {
            LoggerUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
    }

    private static File[] listJars(File dir) {
        if (dir.isDirectory()) {
            return dir.listFiles((f, name) -> {
                if (name.endsWith(".jar")) {// $NON-NLS-1$
                    File jar = new File(f, name);
                    return jar.isFile() && jar.canRead();
                }
                return false;
            });
        }
        return new File[0];
    }

    private void loadPlugJar(String jarPath) {
        File file = new File(jarPath);
        if (file.isDirectory() && !jarPath.endsWith("/")) {// $NON-NLS-1$
            file = new File(jarPath + "/");// $NON-NLS-1$
        }

        File[] jars = listJars(file);
        for (File jarFile : jars) {
            // 从URLClassLoader类中获取类所在文件夹的方法，jar也可以认为是一个文件夹
            Method method = null;
            try {
                method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            } catch (NoSuchMethodException | SecurityException e1) {
                e1.printStackTrace();
            }
            // 获取方法的访问权限以便写回
            try {
                method.setAccessible(true);
                // 获取系统类加载器
                URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();

                URL url = jarFile.toURI().toURL();
                //URLClassLoader classLoader = new URLClassLoader(new URL[]{url});

                method.invoke(classLoader, url);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void putRunningTasks(String key, String value) {
        List<String> list = new ArrayList<>();
        if (this.runningTasks.containsKey(key)) {
            list = this.runningTasks.get(key);
        }
        list.add(value);
        this.runningTasks.put(key, list);
    }

    public int getRunningTasks(String key) {
        if (this.runningTasks.containsKey(key)) {
            return this.runningTasks.get(key).size();
        }
        return 0;
    }

    public int getRunningSize() {
        return this.runningTasks.size();
    }

    public String getRunningList(String key) {
        if (this.runningTasks.containsKey(key)) {
            return JSON.toJSONString(this.runningTasks.get(key));
        }
        return null;
    }

    public void remove(String key, String value) {
        if (this.runningTasks.containsKey(key)) {
            this.runningTasks.get(key).remove(value);
        }
    }

    @Scheduled(cron = "0 0/5 * * * ?")
    public void execute() {
        if (StringUtils.isNotEmpty(url)) {
            FileUtils.deletePath(FileUtils.JAR_FILE_DIR);
            File file = ZipSpider.downloadFile(url, FileUtils.JAR_FILE_DIR);
            if (file != null) {
                ZipSpider.unzip(file.getPath(), FileUtils.JAR_FILE_DIR);
                this.loadJar(FileUtils.JAR_FILE_DIR);
                FileUtils.deleteFile(file.getPath());
            }
            // 清理历史jar
            FileUtils.deletePath(FileUtils.JAR_PLUG_FILE_DIR);
            LoggerUtil.info("开始同步插件JAR：" + plugUrl);
            File plugFile = ZipSpider.downloadFile(plugUrl, FileUtils.JAR_PLUG_FILE_DIR);
            if (plugFile != null) {
                ZipSpider.unzip(plugFile.getPath(), FileUtils.JAR_PLUG_FILE_DIR);
                FileUtils.deleteFile(file.getPath());
                this.loadPlugJar(FileUtils.JAR_PLUG_FILE_DIR);
            }
        }
    }
}
