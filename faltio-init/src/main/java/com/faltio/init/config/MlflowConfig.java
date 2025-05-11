package com.faltio.init.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.mlflow.tracking.MlflowClient;
import org.mlflow.tracking.creds.MlflowHostCreds;
import org.mlflow.tracking.creds.MlflowHostCredsProvider;

@Configuration
public class MlflowConfig {

    @Bean
    public MlflowClient mlflowClient(MlflowProperties props) {
        return new MlflowClient(new MlflowHostCredsProvider() {
            @Override
            public MlflowHostCreds getHostCreds() {
                return new MlflowHostCreds() {
                    @Override public String getHost() { return props.getHost(); }

                    @Override public String getUsername() { return props.getUsername(); }

                    @Override public String getPassword() { return props.getPassword(); }

                    @Override public String getToken() { return ""; }

                    @Override public boolean shouldIgnoreTlsVerification() { return props.isIgnoreTls(); }
                };
            }

            @Override
            public void refresh() {
                // No-op
            }
        });
    }
}
