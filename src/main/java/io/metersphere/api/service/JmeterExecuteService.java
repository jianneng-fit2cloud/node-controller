package io.metersphere.api.service;

import com.alibaba.fastjson.JSON;
import io.metersphere.api.controller.request.RunRequest;
import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.node.util.LogUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.NewDriver;
import org.apache.jmeter.save.SaveService;
import org.apache.jorphan.collections.HashTree;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
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
    private static boolean enable = false;

    // 记录所以执行中的请求/场景
    private Map<String, List<String>> runningTasks = new HashMap<>();

    private static InputStream getStrToStream(String sInputString) {
        if (StringUtils.isNotEmpty(sInputString)) {
            try {
                ByteArrayInputStream tInputStringStream = new ByteArrayInputStream(sInputString.getBytes());
                return tInputStringStream;
            } catch (Exception ex) {
                ex.printStackTrace();
                MSException.throwException("生成脚本异常");
            }
        }
        return null;
    }

    private void loadJar(String path) {
        try {
            NewDriver.addPath(path);
        } catch (MalformedURLException e) {
            LogUtil.error(e.getMessage(), e);
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

    public String run(RunRequest request, MultipartFile[] bodyFiles, MultipartFile[] jarFiles) {
        if (request == null || request.getJmx() == null) {
            return "执行文件为空，无法执行！";
        }
        LogUtil.info(request.getJmx());
        // 生成附件/JAR文件
        FileUtils.createFiles(bodyFiles, FileUtils.BODY_FILE_DIR);
        FileUtils.createFiles(jarFiles, FileUtils.JAR_FILE_DIR);
        try {
            this.loadJar(FileUtils.JAR_FILE_DIR);
            // 生成执行脚本
            InputStream inputSource = getStrToStream(request.getJmx());
            Object scriptWrapper = SaveService.loadElement(inputSource);
            HashTree testPlan = JMeterService.getHashTree(scriptWrapper);
            // 开始执行
            jMeterService.run(request, testPlan);
        } catch (Exception e) {
            LogUtil.error(e.getMessage());
            return e.getMessage();
        }
        return "SUCCESS";
    }

    public String runStart(RunRequest runRequest) {
        try {
            if (runRequest != null && StringUtils.isNotEmpty(runRequest.getAmassReport())) {
                this.putRunningTasks(runRequest.getAmassReport(), runRequest.getTestId());
            }
            if (runRequest.getKafka() == null) {
                return "KAFKA 初始化失败，请检查配置";
            }
            // 生成附件/JAR文件
            URL urlObject = new URL(runRequest.getUrl());
            String jarUrl = urlObject.getProtocol() + "://" + urlObject.getHost() + (urlObject.getPort() > 0 ? ":" + urlObject.getPort() : "") + "/api/jmeter/download/jar";
            String plugJarUrl = urlObject.getProtocol() + "://" + urlObject.getHost() + (urlObject.getPort() > 0 ? ":" + urlObject.getPort() : "") + "/api/jmeter/download/plug/jar";

            if (StringUtils.isEmpty(url)) {
                LogUtil.info("开始同步上传的JAR：" + jarUrl);
                File file = ZipSpider.downloadFile(jarUrl, FileUtils.JAR_FILE_DIR);
                if (file != null) {
                    ZipSpider.unzip(file.getPath(), FileUtils.JAR_FILE_DIR);
                    this.loadJar(FileUtils.JAR_FILE_DIR);
                }
                LogUtil.info("开始同步插件JAR：" + plugJarUrl);
                File plugFile = ZipSpider.downloadFile(plugJarUrl, FileUtils.JAR_PLUG_FILE_DIR);
                if (plugFile != null) {
                    ZipSpider.unzip(plugFile.getPath(), FileUtils.JAR_PLUG_FILE_DIR);
                    this.loadPlugJar(FileUtils.JAR_PLUG_FILE_DIR);
                }
            }
            url = jarUrl;
            this.enable = runRequest.isEnable();
            LogUtil.info("开始拉取脚本和脚本附件：" + runRequest.getUrl());

            File bodyFile = ZipSpider.downloadFile(runRequest.getUrl(), FileUtils.BODY_FILE_DIR);
            if (bodyFile != null) {
                ZipSpider.unzip(bodyFile.getPath(), FileUtils.BODY_FILE_DIR);
                String fileName = runRequest.getReportId() + "_" + runRequest.getTestId() + ".jmx";
                LogUtil.info("获取到hashTree文件：" + fileName);
                File jmxFile = new File(FileUtils.BODY_FILE_DIR + "/" + fileName);
                // 生成执行脚本
                HashTree testPlan = SaveService.loadTree(jmxFile);
                // 开始执行
                jMeterService.run(runRequest, testPlan);
                FileUtils.deleteFile(bodyFile.getPath());
            } else {
                MSException.throwException("未找到执行的JMX文件");
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage());
            return e.getMessage();
        }
        return "SUCCESS";
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
        if (StringUtils.isNotEmpty(url) && enable) {
            LogUtil.info("开始定时同步jar");
            File file = ZipSpider.downloadFile(url, FileUtils.JAR_FILE_DIR);
            if (file != null) {
                ZipSpider.unzip(file.getPath(), FileUtils.JAR_FILE_DIR);
                this.loadJar(FileUtils.JAR_FILE_DIR);
                FileUtils.deleteFile(file.getPath());
            }
        }
    }
}
