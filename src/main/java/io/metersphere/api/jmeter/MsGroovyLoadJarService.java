package io.metersphere.api.jmeter;

import groovy.lang.GroovyClassLoader;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.jmeter.LoadJarService;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class MsGroovyLoadJarService implements LoadJarService {

    public void loadGroovyJar(GroovyClassLoader classLoader) {
        try {
            LoggerUtil.info("开始加载 GroovyJar ");
            File file = new File(FileUtils.JAR_FILE_DIR);
            if (file.isFile()) {
                classLoader.addURL(file.toURI().toURL());
            } else {
                File[] files = file.listFiles();
                if (files != null && files.length > 0) {
                    for (File f : files) {
                        if (StringUtils.isNotEmpty(f.getPath()) && f.getPath().endsWith(".jar")) {
                            classLoader.addURL(f.toURI().toURL());
                        }
                    }
                }
            }
            LoggerUtil.info("加载 GroovyJar 完成");
        } catch (Exception e) {
            LoggerUtil.error(e.getMessage(), e);
        }
    }

}
