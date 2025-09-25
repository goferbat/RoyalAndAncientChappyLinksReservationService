package org.chappyGolf.config;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

import javax.sql.DataSource;

@Configuration
public class CayenneConfig {

    @Bean
    public ServerRuntime cayenneRuntime(DataSource dataSource) {
        return ServerRuntime.builder()
                .addConfig("cayenne-project.xml")
                .dataSource(dataSource)   // hand off Spring's pooled DataSource
                .build();
    }

    @Bean
    @RequestScope
    public ObjectContext cayenneContext(ServerRuntime runtime) {
        return runtime.newContext();
    }
}