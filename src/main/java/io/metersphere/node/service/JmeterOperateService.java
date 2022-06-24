package io.metersphere.node.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.InvocationBuilder;
import io.metersphere.node.controller.request.TestRequest;
import io.metersphere.node.util.CompressUtils;
import io.metersphere.node.util.DockerClientService;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrSubstitutor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

@Service
public class JmeterOperateService {
    @Resource
    private KafkaProducerService kafkaProducerService;

    public void startContainer(TestRequest testRequest) {
//        Map<String, String> env = testRequest.getEnv();
//        String testId = env.get("TEST_ID");
//        LoggerUtil.info("Receive start container request, test id: {}", testId);
//        String bootstrapServers = env.get("BOOTSTRAP_SERVERS");
//        // 检查kafka连通性
//        checkKafka(bootstrapServers);
//        // 初始化kafka
//        kafkaProducerService.init(bootstrapServers);
//
//        DockerClient dockerClient = DockerClientService.connectDocker(testRequest);
//
//        String containerImage = testRequest.getImage();
//
//        // 查找镜像
//        searchImage(dockerClient, testRequest.getImage());
//        // 检查容器是否存在
//        checkContainerExists(dockerClient, testId);
//        // 启动测试
//        startContainer(testRequest, dockerClient, testId, containerImage);
        modifyRunTest(testRequest);
    }

    private void startContainer(TestRequest testRequest, DockerClient dockerClient, String testId, String containerImage) {
        // 创建 hostConfig
        HostConfig hostConfig = HostConfig.newHostConfig();
        hostConfig.withNetworkMode("host");
        String[] envs = getEnvs(testRequest);
        String containerId = DockerClientService.createContainers(dockerClient, testId, containerImage, hostConfig, envs).getId();

        DockerClientService.startContainer(dockerClient, containerId);
        LoggerUtil.info("Container create started containerId: " + containerId);

        String topic = testRequest.getEnv().getOrDefault("LOG_TOPIC", "JMETER_LOGS");
        String reportId = testRequest.getEnv().get("REPORT_ID");

        dockerClient.waitContainerCmd(containerId)
                .exec(new WaitContainerResultCallback() {
                    @Override
                    public void onComplete() {
                        // 清理文件夹
                        try {
                            if (DockerClientService.existContainer(dockerClient, containerId) > 0) {
//                                copyTestResources(dockerClient, containerId, reportId, resourceIndex);
                                DockerClientService.removeContainer(dockerClient, containerId);
                            }
                            LoggerUtil.info("Remove container completed: " + containerId);
                        } catch (Exception e) {
                            LoggerUtil.error("Remove container error: ", e);
                        }
                        LoggerUtil.info("completed....");
                    }
                });

        dockerClient.logContainerCmd(containerId)
                .withFollowStream(true)
                .withStdOut(true)
                .withStdErr(true)
                .withTailAll()
                .exec(new InvocationBuilder.AsyncResultCallback<Frame>() {
                    @Override
                    public void onNext(Frame item) {
                        String log = new String(item.getPayload()).trim();
                        String oomMessage = "There is insufficient memory for the Java Runtime Environment to continue.";
                        if (StringUtils.contains(log, oomMessage)) {
                            // oom 退出
                            String[] contents = new String[]{reportId, "none", "0", oomMessage};
                            String message = StringUtils.join(contents, " ");
                            kafkaProducerService.sendMessage(topic, message);
                        }
                        LoggerUtil.info(log);
                    }
                });
    }

    private void copyTestResources(DockerClient dockerClient, String containerId, String reportId, String resourceIndex) throws IOException {
        InputStream testIn = dockerClient
                .copyArchiveFromContainerCmd(containerId, "/test/")
                .exec();
        testIn.available();
        String pathname = reportId + "_" + resourceIndex;
        File dir = new File(pathname);

        FileUtils.forceMkdir(dir);
        File testDir = new File(pathname + "/test.tar");
        FileUtils.copyInputStreamToFile(testIn, testDir);


        InputStream jtlIn = dockerClient
                .copyArchiveFromContainerCmd(containerId, "/jmeter-log/" + reportId + "_" + resourceIndex + ".jtl")
                .exec();
        jtlIn.available();
        File jtl = new File(pathname + "/report.jtl.tar");
        FileUtils.copyInputStreamToFile(jtlIn, jtl);

        InputStream logIn = dockerClient
                .copyArchiveFromContainerCmd(containerId, "/jmeter-log/jmeter.log")
                .exec();
        logIn.available();
        File log = new File(pathname + "/jmeter.log.tar");
        FileUtils.copyInputStreamToFile(logIn, log);

        InputStream generateLogIn = dockerClient
                .copyArchiveFromContainerCmd(containerId, "/jmeter-log/generate-report.log")
                .exec();
        generateLogIn.available();
        File generateLog = new File(pathname + "/generate-report.log.tar");
        FileUtils.copyInputStreamToFile(generateLogIn, generateLog);

        try (
                ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(reportId + "_" + resourceIndex + ".zip"))
        ) {
            CompressUtils.zipDirectory(dir, zipOutputStream, "");
            FileUtils.forceDelete(dir);
        }
    }

    private void checkContainerExists(DockerClient dockerClient, String testId) {
        List<Container> list = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withStatusFilter(Arrays.asList("created", "restarting", "running", "paused", "exited"))
                .withNameFilter(Collections.singletonList(testId))
                .exec();
        if (!CollectionUtils.isEmpty(list)) {
            list.forEach(container -> DockerClientService.removeContainer(dockerClient, container.getId()));
        }
    }

    private void checkKafka(String bootstrapServers) {
        String[] servers = StringUtils.split(bootstrapServers, ",");
        try {
            for (String s : servers) {
                String[] ipAndPort = s.split(":");
                //1,建立tcp
                String ip = ipAndPort[0];
                int port = Integer.parseInt(ipAndPort[1]);
                try (
                        Socket soc = new Socket()
                ) {
                    soc.connect(new InetSocketAddress(ip, port), 1000); // 1s timeout
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(e);
            throw new RuntimeException("Failed to connect to Kafka");
        }
    }

    private String[] getEnvs(TestRequest testRequest) {
        Map<String, String> env = testRequest.getEnv();
        return env.keySet().stream().map(k -> k + "=" + env.get(k)).toArray(String[]::new);
    }

    private void searchImage(DockerClient dockerClient, String imageName) {
        // image
        List<Image> imageList = dockerClient.listImagesCmd().exec();
        if (CollectionUtils.isEmpty(imageList)) {
            throw new RuntimeException("Image List is empty");
        }
        List<Image> collect = imageList.stream().filter(image -> {
            String[] repoTags = image.getRepoTags();
            if (repoTags == null) {
                return false;
            }
            for (String repoTag : repoTags) {
                if (repoTag.equals(imageName)) {
                    return true;
                }
            }
            return false;
        }).collect(Collectors.toList());

        if (collect.size() == 0) {
            throw new RuntimeException("Image Not Found: " + imageName);
        }
    }


    public void stopContainer(String testId) {
        LoggerUtil.info("Receive stop container request, test: {}", testId);
        DockerClient dockerClient = DockerClientService.connectDocker();

        // container filter
        List<Container> list = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withStatusFilter(Collections.singletonList("running"))
                .withNameFilter(Collections.singletonList(testId))
                .exec();
        // container stop
        list.forEach(container -> DockerClientService.removeContainer(dockerClient, container.getId()));
    }

    public List<Container> taskStatus(String testId) {
        DockerClient dockerClient = DockerClientService.connectDocker();
        List<Container> containerList = dockerClient.listContainersCmd()
                .withStatusFilter(Arrays.asList("created", "restarting", "running", "paused", "exited"))
                .withNameFilter(Collections.singletonList(testId))
                .exec();
        // 查询执行的状态
        return containerList;
    }

    public String logContainer(String testId) {
        LoggerUtil.info("Receive logs container request, test: {}", testId);
        DockerClient dockerClient = DockerClientService.connectDocker();

        // container filter
        List<Container> list = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withStatusFilter(Collections.singletonList("running"))
                .withNameFilter(Collections.singletonList(testId))
                .exec();

        StringBuilder sb = new StringBuilder();
        if (list.size() > 0) {
            try {
                dockerClient.logContainerCmd(list.get(0).getId())
                        .withFollowStream(true)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withTailAll()
                        .exec(new InvocationBuilder.AsyncResultCallback<Frame>() {
                            @Override
                            public void onNext(Frame item) {
                                sb.append(new String(item.getPayload()).trim()).append("\n");
                            }
                        }).awaitCompletion(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                LoggerUtil.error(e);
            }
        }
        return sb.toString();
    }

    private void modifyRunTest(TestRequest testRequest){
        Map<String, String> env = testRequest.getEnv();
        //String topic = env.getOrDefault("LOG_TOPIC", "JMETER_LOGS");
        String runTestContent = getRunTestContent();
        Map<String, String> map = new HashMap<>();
        map.put("METERSPHERE_URL", env.get("METERSPHERE_URL"));
        map.put("TEST_ID", env.get("TEST_ID"));
        map.put("RESOURCE_ID", env.get("RESOURCE_ID"));
        map.put("RATIO", env.get("RATIO"));
        map.put("REPORT_ID", env.get("REPORT_ID"));
        map.put("RESOURCE_INDEX", env.get("RESOURCE_INDEX"));
        map.put("TESTS_DIR", env.get("TESTS_DIR"));
        map.put("BACKEND_LISTENER", env.get("BACKEND_LISTENER"));
        map.put("GRANULARITY", env.get("GRANULARITY"));
        StrSubstitutor strSubstitutor = new StrSubstitutor(map);
        String modifyRunTestContent = strSubstitutor.replace(runTestContent);
        System.out.println("modify:"+modifyRunTestContent);
        try {
            createShell("/opt/run-test.sh",modifyRunTestContent);
            runShell("/opt/run-test.sh");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public String getRunTestContent() {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            InputStream inputStream = loader.getResourceAsStream("jmeter/run-test.sh");
            byte[] bytes = readInputStream(inputStream);
            inputStream.close();
            return new String(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public byte[] readInputStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int len = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.close();
        return bos.toByteArray();
    }

    public void createShell(String path, String... strs) throws Exception {
        LoggerUtil.info("开始创建:",path);
        if (strs == null) {
            return;
        }
        File sh = new File(path);
        if (sh.exists()) {
            sh.delete();
        }
        sh.createNewFile();
        sh.setExecutable(true);
        FileWriter fw = new FileWriter(sh);
        BufferedWriter bf = new BufferedWriter(fw);
        for (int i = 0; i < strs.length; i++) {
            bf.write(strs[i]);
            if (i < strs.length - 1) {
                bf.newLine();
            }
        }
        bf.flush();
        bf.close();
        LoggerUtil.info("结束创建");
    }

    public void runShell(String directory) throws Exception {
        LoggerUtil.info("开始执行:"+directory);
        ProcessBuilder processBuilder = new ProcessBuilder(directory);
        //Sets the source and destination for subprocess standard I/O to be the same as those of the current Java process.
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        int exitValue = process.waitFor();
        if (exitValue != 0) {
            // check for errors
            new BufferedInputStream(process.getErrorStream());
            throw new RuntimeException("execution of script failed!");
        }
        LoggerUtil.info("结束执行");
    }
}
