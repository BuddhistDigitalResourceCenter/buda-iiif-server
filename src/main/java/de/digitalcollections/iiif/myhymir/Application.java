package de.digitalcollections.iiif.myhymir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import de.digitalcollections.iiif.myhymir.backend.impl.repository.S3ResourceRepositoryImpl;
import io.bdrc.auth.AuthProps;
import io.bdrc.auth.rdf.RdfAuthModel;

@SpringBootApplication
@Configuration
@EnableAutoConfiguration
@Primary
@ComponentScan(basePackages = { "de.digitalcollections.core.business.impl.service", "io.bdrc.archives", "io.bdrc.iiif", "de.digitalcollections.iiif.hymir", "de.digitalcollections.iiif.myhymir",
        "de.digitalcollections.core.backend.impl.file.repository.resource.util" })

//REMINDER : DO NOT REMOVE THE FOLLOWING COMMENTS
/*
 * , excludeFilters = { @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
 * value = ReferencedResourcePersistenceTypeHandler.class),
 * 
 * @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value =
 * ManagedResourcePersistenceTypeHandler.class), @ComponentScan.Filter(type =
 * FilterType.ASSIGNABLE_TYPE, value =
 * ResolvedResourcePersistenceTypeHandler.class) } type =
 * FilterType.ASSIGNABLE_TYPE, value = {ResourceRepositoryImpl.class}))
 */

public class Application extends SpringBootServletInitializer {

    static final String configPath = System.getProperty("iiifserv.configpath");
    public static final Logger perf = LoggerFactory.getLogger("performance");
    private static Properties props;

    public static void main(String[] args) throws Exception {
        InputStream input = new FileInputStream(new File(configPath + "iiifserv.properties"));
        props = new Properties();
        props.load(input);
        try {
            InputStream is = new FileInputStream("/etc/buda/share/shared-private.properties");
            props.load(is);

        } catch (Exception ex) {
            // do nothing, continue props initialization
        }
        AuthProps.init(props);
        if ("true".equals(props.getProperty("authEnabled"))) {

            RdfAuthModel.init();
        }
        S3ResourceRepositoryImpl.initWithProps(props);
        SpringApplication.run(Application.class, args);
        perf.debug("Application main", "Test PERF Log ");
    }

    public static void initForTests() throws IOException {
        InputStream input = Application.class.getClassLoader().getResourceAsStream("iiifservTest.properties");
        props = new Properties();
        props.load(input);
        try {
            InputStream is = new FileInputStream("/etc/buda/share/shared-private.properties");
            props.load(is);

        } catch (Exception ex) {
            // do nothing, continue props initialization
        }
    }

    public static String getProperty(String key) {
        return props.getProperty(key);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class);
    }

}