package io.metersphere.api.jmeter.utils;

import io.metersphere.utils.LoggerUtil;
import org.aspectj.util.FileUtil;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

public class FileUtils {
    public static final String BODY_FILE_DIR = "/opt/metersphere/data/body";
    public static final String JAR_FILE_DIR = "/opt/metersphere/data/node/jar";
    public static final String JAR_PLUG_FILE_DIR = "/opt/metersphere/data/node/plug/jar";

    public static void createFiles(MultipartFile[] bodyFiles, String path) {
        if (bodyFiles != null && bodyFiles.length > 0) {
            File testDir = new File(path);
            if (!testDir.exists()) {
                testDir.mkdirs();
            }
            for (int i = 0; i < bodyFiles.length; i++) {
                MultipartFile item = bodyFiles[i];
                File file = new File(path + "/" + item.getOriginalFilename());
                try (InputStream in = item.getInputStream(); OutputStream out = new FileOutputStream(file)) {
                    file.createNewFile();
                    FileUtil.copyStream(in, out);
                } catch (IOException e) {
                    LoggerUtil.error(e);
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

    private static File[] getFiles(File dir) {
        return dir.listFiles((f, name) -> {
            File jar = new File(f, name);
            return jar.isFile() && jar.canRead();
        });
    }


    public static void deletePath(String path) {
        File file = new File(path);
        if (file.isDirectory()) {// $NON-NLS-1$
            file = new File(path + "/");
        }

        File[] files = getFiles(file);
        for (int i = 0; i < files.length; i++) {
            files[i].delete();
        }
    }
}
