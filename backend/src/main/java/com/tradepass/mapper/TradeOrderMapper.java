package com.tradepass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradepass.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {
    @Select("""
        SELECT counterparty_name AS counterpartyName, SUM(amount) AS totalAmount, COUNT(1) AS orderCount
        FROM trade_order
        WHERE company_id = #{companyId}
          AND direction = #{direction}
          AND (#{period} = 'total'
            OR (#{period} = 'year' AND YEAR(order_date) = YEAR(CURDATE()))
            OR (#{period} = 'month' AND YEAR(order_date) = YEAR(CURDATE()) AND MONTH(order_date) = MONTH(CURDATE())))
        GROUP BY counterparty_name
        ORDER BY totalAmount DESC
        """)
    List<Map<String, Object>> selectRanking(@Param("companyId") Long companyId,
                                            @Param("direction") String direction,
                                            @Param("period") String period);
}
