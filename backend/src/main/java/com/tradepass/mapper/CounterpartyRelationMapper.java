package com.tradepass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradepass.entity.CounterpartyRelationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CounterpartyRelationMapper extends BaseMapper<CounterpartyRelationEntity> {
    @Select("""
        SELECT r.id, COALESCE(c.name, r.counterparty_company_name) AS counterpartyName,
               'SUPPLIER' AS relationType, r.status
        FROM counterparty_relation r
        LEFT JOIN company c ON c.id = r.counterparty_company_id
        WHERE r.company_id = #{companyId} AND r.status = 'ACTIVE'
        ORDER BY r.id
        """)
    List<Map<String, Object>> selectBuyerCounterparties(@Param("companyId") Long companyId);

    @Select("""
        SELECT r.id, COALESCE(c.name, '未知企业') AS counterpartyName,
               'CUSTOMER' AS relationType, r.status
        FROM counterparty_relation r
        LEFT JOIN company c ON c.id = r.company_id
        WHERE r.counterparty_company_id = #{companyId} AND r.status = 'ACTIVE'
        ORDER BY r.id
        """)
    List<Map<String, Object>> selectSupplierCounterparties(@Param("companyId") Long companyId);
}
