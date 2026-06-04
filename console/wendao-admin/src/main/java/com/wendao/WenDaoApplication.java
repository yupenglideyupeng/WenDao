package com.wendao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author wendao
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class WenDaoApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(WenDaoApplication.class, args);
    }
}
