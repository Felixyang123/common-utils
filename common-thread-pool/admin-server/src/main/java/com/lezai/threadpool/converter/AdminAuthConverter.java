package com.lezai.threadpool.converter;

import com.lezai.threadpool.pojo.response.AdminLoginResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Duration;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdminAuthConverter {

    @Mapping(target = "token", source = "token")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "expiresInSeconds", expression = "java(expiry.getSeconds())")
    AdminLoginResponse toLoginResponse(String token, String username, Duration expiry);
}
