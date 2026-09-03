package com.tradepass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradepass.entity.Company;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyMapper extends BaseMapper<Company> {
    @Select("SELECT * FROM company WHERE id = #{id} FOR UPDATE")
    Company selectByIdForUpdate(@Param("id") Long id);
}
