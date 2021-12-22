package io.metersphere.api.jmeter;

import groovy.lang.GroovyClassLoader;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class GroovyLoadJarService {

    public void loadGroovyJar(GroovyClassLoader classLoader) {
        try {
            File file = new File(FileUtils.JAR_FILE_DIR);
            if (file.isFile()) {
                classLoader.addURL(file.toURI().toURL());
            } else {
                File[] files = file.listFiles();
                if (files != null && files.length > 0) {
                    for (File f : files) {
                        classLoader.addURL(f.toURI().toURL());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            LoggerUtil.error(e.getMessage(), e);
        }
    }

}
