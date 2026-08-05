package com.example.project.service.impl;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.dto.request.CouponCreateRequest;
import com.example.project.dto.response.CouponResponse;
import com.example.project.entity.Coupon;
import com.example.project.entity.UserCoupon;
import com.example.project.mapper.CouponMapper;
import com.example.project.mapper.UserCouponMapper;
import com.example.project.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 优惠券服务实现
 * <p>
 * 领取防超发：couponMapper.incrementClaimCount 条件更新（used_count &lt; total_count）原子占额度；
 * 防一券多用：冻结/使用/解冻全部条件更新（WHERE status=旧态）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long claim(Long userId, Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !"ACTIVE".equals(coupon.getStatus())) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }
        if (coupon.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        // Q2：每用户每模板限领 1 张
        if (userCouponMapper.countByUserAndCoupon(userId, couponId) > 0) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_CLAIMED);
        }
        // 原子占额度：影响行数=0 → 已领完/失效
        int affected = couponMapper.incrementClaimCount(couponId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COUPON_STOCK_RUN_OUT);
        }
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        userCouponMapper.insert(uc);
        log.info("[优惠券] 领取成功：userId={}, couponId={}, userCouponId={}", userId, couponId, uc.getId());
        return uc.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal freezeForOrder(Long userCouponId, Long userId, String orderNo, BigDecimal orderTotal) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null || !"ACTIVE".equals(coupon.getStatus())) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }
        if (coupon.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        if (orderTotal.compareTo(coupon.getMinAmount()) < 0) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE);
        }
        // 条件冻结 UNUSED→FROZEN：已被其他订单冻结/使用 → 影响行数=0
        if (userCouponMapper.freezeCoupon(userCouponId, orderNo) == 0) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
        }
        log.info("[优惠券] 冻结成功：userCouponId={}, orderNo={}, discount={}", userCouponId, orderNo, coupon.getDiscount());
        return coupon.getDiscount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markUsed(Long userCouponId, String orderNo) {
        if (userCouponMapper.useCoupon(userCouponId, orderNo) == 0) {
            UserCoupon uc = userCouponMapper.selectById(userCouponId);
            // 幂等：已 USED 且同单 → 视为成功（重复投递场景）
            if (uc != null && "USED".equals(uc.getStatus()) && orderNo.equals(uc.getOrderNo())) {
                log.info("[优惠券] 幂等跳过：userCouponId={} 已 USED（orderNo={}）", userCouponId, orderNo);
                return;
            }
            throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
        }
        log.info("[优惠券] 使用成功：userCouponId={}, orderNo={}", userCouponId, orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long userCouponId) {
        if (userCouponMapper.unfreezeCoupon(userCouponId) == 0) {
            UserCoupon uc = userCouponMapper.selectById(userCouponId);
            // 幂等：已 UNUSED → 视为成功（重复投递场景）
            if (uc != null && "UNUSED".equals(uc.getStatus())) {
                log.info("[优惠券] 幂等跳过：userCouponId={} 已解冻", userCouponId);
                return;
            }
            // 已 USED 不可解冻（订单已支付成功后再收到取消消息属于异常时序）
            throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
        }
        log.info("[优惠券] 解冻成功：userCouponId={}", userCouponId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponse> listMyCoupons(Long userId, String status) {
        List<UserCoupon> userCoupons = userCouponMapper.selectByUserId(userId);
        List<CouponResponse> responses = new ArrayList<>();
        for (UserCoupon uc : userCoupons) {
            if (status != null && !status.isEmpty() && !status.equals(uc.getStatus())) {
                continue;
            }
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            CouponResponse resp = new CouponResponse();
            resp.setId(uc.getId());
            resp.setUserId(uc.getUserId());
            resp.setCouponId(uc.getCouponId());
            resp.setOrderNo(uc.getOrderNo());
            resp.setStatus(uc.getStatus());
            resp.setFrozenAt(uc.getFrozenAt());
            resp.setUsedAt(uc.getUsedAt());
            resp.setCreatedAt(uc.getCreatedAt());
            if (coupon != null) {
                resp.setName(coupon.getName());
                resp.setDiscount(coupon.getDiscount());
                resp.setMinAmount(coupon.getMinAmount());
                resp.setExpireAt(coupon.getExpireAt());
            }
            responses.add(resp);
        }
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCoupon(CouponCreateRequest request) {
        Coupon coupon = new Coupon();
        coupon.setName(request.getName());
        coupon.setDiscount(request.getDiscount());
        coupon.setMinAmount(request.getMinAmount() == null ? BigDecimal.ZERO : request.getMinAmount());
        coupon.setTotalCount(request.getTotalCount());
        coupon.setUsedCount(0);
        coupon.setStatus("ACTIVE");
        coupon.setExpireAt(request.getExpireAt());
        couponMapper.insert(coupon);
        log.info("[优惠券] 建券成功：id={}, name={}, totalCount={}", coupon.getId(), coupon.getName(), coupon.getTotalCount());
        return coupon.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Coupon> listAvailable() {
        return couponMapper.selectAvailable();
    }
}
