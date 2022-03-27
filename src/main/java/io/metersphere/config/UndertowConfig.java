package io.metersphere.config;


import io.undertow.server.handlers.DisallowedMethodsHandler;
import io.undertow.util.HttpString;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class UndertowConfig {


    @Bean
    public ServletWebServerFactory undertowFactory() {
        UndertowServletWebServerFactory undertowFactory = new UndertowServletWebServerFactory();
        // 禁用 TRACE 和 TRACK
        undertowFactory.addDeploymentInfoCustomizers(deploymentInfo -> deploymentInfo.addInitialHandlerChainWrapper(handler -> {
            HttpString[] disallowedHttpMethods = {HttpString.tryFromString("TRACE"), HttpString.tryFromString("TRACK")};
            return new DisallowedMethodsHandler(handler, disallowedHttpMethods);
        }));
        return undertowFactory;
    }

}

