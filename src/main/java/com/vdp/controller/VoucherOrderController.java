package com.vdp.controller;


import com.vdp.dto.Result;
import com.vdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 获取秒杀幂等 Token；下单时在请求头携带 X-Idempotency-Token
     */
    @PostMapping("/idempotent-token")
    public Result issueIdempotentToken() {
        return voucherOrderService.issueIdempotentToken();
    }

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(
            @PathVariable("id") Long voucherId,
            @RequestHeader(value = "X-Idempotency-Token", required = false) String idempotencyToken
    ) {
        return voucherOrderService.seckillVoucher(voucherId, idempotencyToken);
    }
}
