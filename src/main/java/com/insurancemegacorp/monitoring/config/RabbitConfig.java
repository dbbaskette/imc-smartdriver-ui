package com.insurancemegacorp.monitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RabbitConfig {

    // This monitoring UI only needs HTTP access to RabbitMQ Management API
    // No direct AMQP connection required - just REST API calls for metrics

    @Bean
    public RestTemplate restTemplate() throws Exception {
        // Create RestTemplate with relaxed SSL validation for CF environments
        return createRestTemplateWithRelaxedSsl();
    }
    
    private RestTemplate createRestTemplateWithRelaxedSsl() throws Exception {
        // Create a trust manager that accepts all certificates
        javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
            new javax.net.ssl.X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
            }
        };

        // Install the all-trusting trust manager
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        
        // Create hostname verifier that accepts all hostnames
        javax.net.ssl.HostnameVerifier allHostsValid = (hostname, session) -> true;

        // Create HTTP client factory with custom SSL context
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        
        if (sslContext != null) {
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        }
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // Add timeout configuration
        factory.setConnectTimeout(10000); // 10 seconds
        factory.setReadTimeout(15000);    // 15 seconds
        
        return restTemplate;
    }
}