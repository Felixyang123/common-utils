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
            @RequestParam(defaultValue = "20") int page_size,
            @RequestParam(required = false) String biz_type,
            @RequestParam(required = false) String operate_type,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String biz_id) {
        PageResult<OperateLogEntity> result = operateLogService.queryLogs(
                page, Math.min(page_size, 100), biz_type, operate_type, operator, biz_id);
        return ApiResponse.success(result);
    }
}
