package com.tradepass.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tradepass.common.AuthContext;
import com.tradepass.common.BusinessException;
import com.tradepass.entity.Company;
import com.tradepass.entity.MemberRemovalNotice;
import com.tradepass.mapper.CompanyMapper;
import com.tradepass.mapper.MemberRemovalNoticeMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MemberRemovalNoticeService {
    public record Notice(String id, String companyId, String companyName, LocalDateTime removedAt) {}
    private final MemberRemovalNoticeMapper noticeMapper;
    private final CompanyMapper companyMapper;

    public MemberRemovalNoticeService(MemberRemovalNoticeMapper noticeMapper, CompanyMapper companyMapper) {
        this.noticeMapper = noticeMapper;
        this.companyMapper = companyMapper;
    }

    public void recordRemoval(long userId, long companyId) {
        Company company = companyMapper.selectById(companyId);
        MemberRemovalNotice notice = new MemberRemovalNotice();
        notice.setUserId(userId);
        notice.setCompanyId(companyId);
        notice.setCompanyName(company == null ? "企业" + companyId : company.getName());
        notice.setRemovedAt(LocalDateTime.now());
        noticeMapper.insert(notice);
    }

    public List<Notice> pending() {
        return noticeMapper.selectPending(AuthContext.userId()).stream()
                .map(n -> new Notice(String.valueOf(n.getId()), String.valueOf(n.getCompanyId()),
                        n.getCompanyName(), n.getRemovedAt())).toList();
    }

    public void acknowledge(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 20) throw new BusinessException("请选择有效的通知");
        List<Long> noticeIds;
        try {
            noticeIds = ids.stream().map(Long::parseLong).distinct().toList();
        } catch (RuntimeException e) {
            throw new BusinessException("通知 ID 格式不正确");
        }
        noticeMapper.update(new LambdaUpdateWrapper<MemberRemovalNotice>()
                .eq(MemberRemovalNotice::getUserId, AuthContext.userId())
                .in(MemberRemovalNotice::getId, noticeIds)
                .isNull(MemberRemovalNotice::getAcknowledgedAt)
                .set(MemberRemovalNotice::getAcknowledgedAt, LocalDateTime.now()));
    }
}
