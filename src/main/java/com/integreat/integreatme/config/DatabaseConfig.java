package com.integreat.integreatme.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
@EnableJpaRepositories(basePackages = "com.integreat.integreatme.config.repositories")
public class DatabaseConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConfig.class);
    private static final String DB_PATH = "data/app.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;

    @Bean
    Connection databaseConnection() throws SQLException, IOException {
        Path dbFile = Paths.get(DB_PATH);
        Path parent = dbFile.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Connection conn = DriverManager.getConnection(JDBC_URL);
        Statement st = conn.createStatement();
        st.execute("PRAGMA journal_mode=WAL");
        st.execute("PRAGMA foreign_keys=ON");
        st.execute("select 1 from sqlite_master");

        LOG.info("SQLite database initialized at: {}", dbFile.toAbsolutePath());

        return conn;
    }

    @Bean
    public DataSource dataSource(Connection connection) {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(org.sqlite.JDBC.class);
        dataSource.setUrl(JDBC_URL);
        return dataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, JpaVendorAdapter jpaVendorAdapter) {
        LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPackagesToScan("com.integreat.integreatme.models");
        entityManagerFactory.setJpaVendorAdapter(jpaVendorAdapter);
        return entityManagerFactory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        return transactionManager;
    }

    @Bean
    public JpaVendorAdapter jpaVendorAdapter() {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setDatabasePlatform("org.hibernate.community.dialect.SQLiteDialect");
        adapter.setShowSql(true);
        return adapter;
    }
}