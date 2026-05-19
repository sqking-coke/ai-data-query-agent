package com.aiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单信息实体（对应 order_info 表）
 */
@Data
@TableName("order_info")
public class OrderInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单编号 */
    private String orderNo;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** 订单状态：0=异常, 1=正常 */
    private Integer orderStatus;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 获取状态的中文描述
     */
    public String getStatusDesc() {
        return orderStatus != null && orderStatus == 1 ? "正常" : "异常";
    }
}