package com.vdp.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

/**
 * Sentinel 规则：秒杀预热限流 + 店铺查询熔断降级。
 */
@Configuration
public class SentinelRuleConfiguration {

    public static final String RESOURCE_SECKILL_ORDER = "seckillOrder";
    public static final String RESOURCE_SHOP_QUERY_BY_ID = "queryShopById";
    public static final String RESOURCE_SHOP_QUERY_BY_TYPE = "queryShopByType";

    @PostConstruct
    public void initRules() {
        loadFlowRules();
        loadDegradeRules();
    }

    private void loadFlowRules() {
        FlowRule seckill = new FlowRule();
        seckill.setResource(RESOURCE_SECKILL_ORDER);
        seckill.setGrade(RuleConstant.FLOW_GRADE_QPS);
        seckill.setCount(50);
        // 预热：冷启动逐步放开到阈值，避免秒杀瞬时打满
        seckill.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP);
        seckill.setWarmUpPeriodSec(12);

        FlowRule shopType = new FlowRule();
        shopType.setResource(RESOURCE_SHOP_QUERY_BY_TYPE);
        shopType.setGrade(RuleConstant.FLOW_GRADE_QPS);
        shopType.setCount(120);

        FlowRule shopId = new FlowRule();
        shopId.setResource(RESOURCE_SHOP_QUERY_BY_ID);
        shopId.setGrade(RuleConstant.FLOW_GRADE_QPS);
        shopId.setCount(200);

        List<FlowRule> rules = Arrays.asList(seckill, shopType, shopId);
        FlowRuleManager.loadRules(rules);
    }

    private void loadDegradeRules() {
        // 平均 RT 熔断：统计窗口内平均响应时间超过阈值则降级（阈值可按压测调整）
        DegradeRule byType = new DegradeRule();
        byType.setResource(RESOURCE_SHOP_QUERY_BY_TYPE);
        byType.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        byType.setCount(600);
        byType.setTimeWindow(15);
        byType.setMinRequestAmount(10);

        DegradeRule byId = new DegradeRule();
        byId.setResource(RESOURCE_SHOP_QUERY_BY_ID);
        byId.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        byId.setCount(600);
        byId.setTimeWindow(15);
        byId.setMinRequestAmount(10);

        List<DegradeRule> rules = Arrays.asList(byType, byId);
        DegradeRuleManager.loadRules(rules);
    }
}
