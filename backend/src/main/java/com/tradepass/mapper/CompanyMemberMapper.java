package com.tradepass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradepass.entity.CompanyMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CompanyMemberMapper extends BaseMapper<CompanyMember> {
    @Select("""
        SELECT u.id AS userId, u.nickname, u.phone, m.role_code AS roleCode, m.status
        FROM sys_user u
        LEFT JOIN company_member m ON u.id = m.user_id AND m.company_id = #{companyId}
        WHERE u.id = #{userId}
        """)
    Map<String, Object> selectMemberInfo(@Param("userId") Long userId, @Param("companyId") Long companyId);

    @Select("""
        SELECT u.id AS userId, u.nickname, u.phone, m.role_code AS roleCode, m.status
        FROM sys_user u
        LEFT JOIN company_member m ON u.id = m.user_id
        WHERE u.id = #{userId}
        LIMIT 1
        """)
    Map<String, Object> selectMemberInfoAnyCompany(@Param("userId") Long userId);

    @Select("""
        SELECT c.id AS companyId, c.name AS companyName, m.role_code AS roleCode
        FROM company c
        JOIN company_member m ON c.id = m.company_id
        WHERE m.user_id = #{userId} AND m.status = 'ACTIVE'
        ORDER BY c.id
        """)
    List<Map<String, Object>> selectUserCompanies(@Param("userId") Long userId);

    @Select("""
        SELECT m.id, m.user_id AS userId, m.role_code AS roleCode, m.status, u.nickname, u.phone
        FROM company_member m
        JOIN sys_user u ON m.user_id = u.id
        WHERE m.company_id = #{companyId}
        ORDER BY m.status, m.id
        """)
    List<Map<String, Object>> selectAuthorizationRecords(@Param("companyId") Long companyId);

    @Select("""
        SELECT m.id, m.user_id AS userId, m.role_code AS roleCode, m.status, u.nickname, u.phone
        FROM company_member m
        JOIN sys_user u ON m.user_id = u.id
        WHERE m.company_id = #{companyId}
          AND (#{status} IS NULL OR #{status} = '' OR m.status = #{status})
        ORDER BY m.status, m.id
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<Map<String, Object>> selectAuthorizationPageRecords(@Param("companyId") Long companyId,
                                                             @Param("status") String status,
                                                             @Param("limit") int limit,
                                                             @Param("offset") long offset);

    @Select("""
        SELECT u.id, u.nickname, u.phone,
               COALESCE(
                   MAX(CASE WHEN m.role_code = 'LEGAL' THEN 'LEGAL' END),
                   MAX(CASE WHEN m.role_code = 'ADMIN' THEN 'ADMIN' END),
                   MAX(m.role_code)
               ) AS roleCode
        FROM sys_user u
        LEFT JOIN company_member m ON u.id = m.user_id
        GROUP BY u.id, u.nickname, u.phone
        ORDER BY u.id
        """)
    List<Map<String, Object>> selectDevUsers();
}
