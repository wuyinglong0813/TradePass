package com.tradepass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("company")
public class Company {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String creditCode;
    private String legalPersonName;
    private String certificationStatus;
    private String realNameStatus;
    private String faceStatus;
    private String sealStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCreditCode() { return creditCode; }
    public void setCreditCode(String creditCode) { this.creditCode = creditCode; }
    public String getLegalPersonName() { return legalPersonName; }
    public void setLegalPersonName(String legalPersonName) { this.legalPersonName = legalPersonName; }
    public String getCertificationStatus() { return certificationStatus; }
    public void setCertificationStatus(String certificationStatus) { this.certificationStatus = certificationStatus; }
    public String getRealNameStatus() { return realNameStatus; }
    public void setRealNameStatus(String realNameStatus) { this.realNameStatus = realNameStatus; }
    public String getFaceStatus() { return faceStatus; }
    public void setFaceStatus(String faceStatus) { this.faceStatus = faceStatus; }
    public String getSealStatus() { return sealStatus; }
    public void setSealStatus(String sealStatus) { this.sealStatus = sealStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
