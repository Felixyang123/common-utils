package com.lezai.threadpool.controller;

import com.lezai.threadpool.bean.ApiResponse;
import com.lezai.threadpool.bean.PageResult;
import com.lezai.threadpool.dao.entity.OperateLogEntity;
import com.lezai.threadpool.service.OperateLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/operate-logs")
@RequiredArgsConstructor
public class OperateLogController {

    private final OperateLogService operateLogService;

    /**
     * 分页查询操作日志
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<OperateLogEntity>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String operateType,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String bizId) {
        PageResult<OperateLogEntity> result = operateLogService.queryLogs(
                page, Math.min(pageSize, 100), bizType, operateType, operator, bizId);
        return ApiResponse.success(result);
    }
}
