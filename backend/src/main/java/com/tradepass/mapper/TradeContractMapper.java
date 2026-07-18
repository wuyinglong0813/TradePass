package com.tradepass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradepass.entity.TradeContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface TradeContractMapper extends BaseMapper<TradeContract> {
    @Select("""
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending,
               COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active,
               COALESCE(SUM(amount), 0) AS amount
        FROM trade_contract
        WHERE company_id = #{companyId}
        """)
    Map<String, Object> selectContractSummary(@Param("companyId") Long companyId);
}
