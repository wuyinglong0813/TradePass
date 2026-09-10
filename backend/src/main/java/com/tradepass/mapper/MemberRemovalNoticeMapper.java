package com.tradepass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradepass.entity.MemberRemovalNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface MemberRemovalNoticeMapper extends BaseMapper<MemberRemovalNotice> {
    @Select("""
        SELECT n.* FROM member_removal_notice n
        WHERE n.user_id = #{userId} AND n.acknowledged_at IS NULL
          AND NOT EXISTS (SELECT 1 FROM company_member m
              WHERE m.user_id = n.user_id AND m.company_id = n.company_id AND m.status = 'ACTIVE')
          AND NOT EXISTS (SELECT 1 FROM member_removal_notice newer
              WHERE newer.user_id = n.user_id AND newer.company_id = n.company_id AND newer.id > n.id)
        ORDER BY n.id DESC LIMIT 20
        """)
    List<MemberRemovalNotice> selectPending(@Param("userId") long userId);
}
