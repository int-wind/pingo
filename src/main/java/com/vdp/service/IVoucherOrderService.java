package com.vdp.service;

import com.vdp.dto.Result;
import com.vdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /** 颁发秒杀下单幂等 Token（单次有效，短时过期） */
    Result issueIdempotentToken();

    Result seckillVoucher(Long voucherId, String idempotencyToken);
}
