package com.lezai.threadpool.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BaseEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = -7900823184170749927L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableLogic(value = "0", delval = "1")
    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
