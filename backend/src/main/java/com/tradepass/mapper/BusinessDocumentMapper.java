package com.tradepass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradepass.entity.BusinessDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BusinessDocumentMapper extends BaseMapper<BusinessDocument> {
    @Select("SELECT * FROM business_document WHERE id = #{id} FOR UPDATE")
    BusinessDocument selectByIdForUpdate(@Param("id") Long id);
}
