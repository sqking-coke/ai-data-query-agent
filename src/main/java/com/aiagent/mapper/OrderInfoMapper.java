package com.aiagent.mapper;

import com.aiagent.entity.OrderInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 订单信息 Mapper
 * <p>
 * 注意：executeRawSql 方法使用 ${sql} 字符串替换，
 * 调用前必须经过 SqlSecurityValidator 安全校验，
 * 不可直接接收前端参数调用此方法。
 */
@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    /**
     * 执行经过安全校验的SELECT语句，返回原始数据
     * <p>
     * 安全前提：调用方已在 SqlSecurityValidator.validate() 中完成了
     * 高危操作拦截、SQL注入检测、表范围校验，此处不做二次校验。
     *
     * @param sql 已通过安全校验的SELECT语句
     * @return 查询结果（每行为一个Map，列名→值）
     */
    @Select("${sql}")
    List<Map<String, Object>> executeRawSql(@Param("sql") String sql);

    /**
     * 按日期范围查询订单
     */
    @Select("SELECT * FROM order_info WHERE create_time >= #{startTime} AND create_time < #{endTime}")
    List<OrderInfo> findByDateRange(@Param("startTime") String startTime,
                                    @Param("endTime") String endTime);

    /**
     * 按状态统计订单数量
     */
    @Select("SELECT order_status, COUNT(*) AS cnt FROM order_info GROUP BY order_status")
    List<Map<String, Object>> countByStatus();
}