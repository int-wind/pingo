package com.vdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.vdp.mapper")
@SpringBootApplication
public class vdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(vdpApplication.class,args);
    }

}
