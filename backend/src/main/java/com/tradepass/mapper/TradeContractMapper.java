package com.tradepass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradepass.entity.TradeContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;
import java.util.List;

@Mapper
public interface TradeContractMapper extends BaseMapper<TradeContract> {
    @Select("""
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending,
               COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END), 0) AS active,
               COALESCE(SUM(amount), 0) AS amount
        FROM trade_contract
        WHERE company_id = #{companyId} OR counterparty_company_id = #{companyId}
        """)
    Map<String, Object> selectContractSummary(@Param("companyId") Long companyId);

    @Select("""
        SELECT t.*
        FROM trade_contract t
        JOIN company initiator ON initiator.id = t.company_id
        WHERE (t.company_id = #{companyId} OR t.counterparty_company_id = #{companyId})
          AND (#{counterpartyName} IS NULL OR #{counterpartyName} = '' OR
               (t.company_id = #{companyId} AND t.counterparty_name = #{counterpartyName}) OR
               (t.counterparty_company_id = #{companyId} AND initiator.name = #{counterpartyName}))
          AND (#{status} IS NULL OR #{status} = '' OR t.status = #{status})
        ORDER BY t.created_at DESC, t.id DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<TradeContract> selectPartyContracts(@Param("companyId") Long companyId,
                                             @Param("counterpartyName") String counterpartyName,
                                             @Param("status") String status,
                                             @Param("limit") int limit,
                                             @Param("offset") long offset);

    @Select("""
        SELECT COUNT(*)
        FROM trade_contract t
        JOIN company initiator ON initiator.id = t.company_id
        WHERE (t.company_id = #{companyId} OR t.counterparty_company_id = #{companyId})
          AND (#{counterpartyName} IS NULL OR #{counterpartyName} = '' OR
               (t.company_id = #{companyId} AND t.counterparty_name = #{counterpartyName}) OR
               (t.counterparty_company_id = #{companyId} AND initiator.name = #{counterpartyName}))
          AND (#{status} IS NULL OR #{status} = '' OR t.status = #{status})
        """)
    long countPartyContracts(@Param("companyId") Long companyId,
                             @Param("counterpartyName") String counterpartyName,
                             @Param("status") String status);
}
