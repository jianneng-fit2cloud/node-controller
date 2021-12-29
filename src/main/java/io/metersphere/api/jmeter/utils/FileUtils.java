package io.metersphere.api.jmeter.utils;

import io.metersphere.node.util.LogUtil;
import org.aspectj.util.FileUtil;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

public class FileUtils {
    public static final String BODY_FILE_DIR = "/opt/metersphere/data/body";
    public static final String JAR_FILE_DIR = "/opt/metersphere/data/node/jar";

    public static void createFiles(MultipartFile[] bodyFiles, String path) {
        if (bodyFiles != null && bodyFiles.length > 0) {
            File testDir = new File(path);
            if (!testDir.exists()) {
                testDir.mkdirs();
            }
            for (int i = 0; i < bodyFiles.length; i++) {
                MultipartFile item = bodyFiles[i];
                File file = new File(path + "/" + item.getOriginalFilename());
                File parentFile = file.getParentFile();
                if (!parentFile.exists()) {
                    parentFile.mkdirs();
                }
                try (InputStream in = item.getInputStream(); OutputStream out = new FileOutputStream(file)) {
                    file.createNewFile();
                    FileUtil.copyStream(in, out);
                } catch (IOException e) {
                    LogUtil.error(e);
                    MSException.throwException("文件处理异常");
                }
            }
        }
    }

    public static void deleteFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

}
