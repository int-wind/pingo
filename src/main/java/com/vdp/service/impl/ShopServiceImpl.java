package com.vdp.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vdp.config.SentinelRuleConfiguration;
import com.vdp.dto.Result;
import com.vdp.entity.Shop;
import com.vdp.mapper.ShopMapper;
import com.vdp.service.IShopService;
import com.vdp.utils.CacheClient;
import com.vdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.vdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    @SentinelResource(
            value = SentinelRuleConfiguration.RESOURCE_SHOP_QUERY_BY_ID,
            blockHandler = "queryByIdBlock",
            fallback = "queryByIdFallback"
    )
    public Result queryById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 7.返回
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    @SentinelResource(
            value = SentinelRuleConfiguration.RESOURCE_SHOP_QUERY_BY_TYPE,
            blockHandler = "queryShopByTypeBlock",
            fallback = "queryShopByTypeFallback"
    )
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            return queryShopPageByType(typeId, current);
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id（Redis 未预热 shop:geo:{typeId} 时为空，回退数据库以免分类列表空白）
        if (results == null) {
            return queryShopPageByType(typeId, current);
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list == null || list.isEmpty()) {
            return queryShopPageByType(typeId, current);
        }
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop，按 Geo 顺序排序（避免 MySQL 专有 ORDER BY FIELD，兼容 SQL Server 等）
        List<Shop> shops = query().in("id", ids).list();
        Map<Long, Shop> byId = new HashMap<>(shops.size());
        for (Shop s : shops) {
            byId.put(s.getId(), s);
        }
        List<Shop> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Shop shop = byId.get(id);
            if (shop == null) {
                continue;
            }
            Distance dist = distanceMap.get(id.toString());
            if (dist != null) {
                shop.setDistance(dist.getValue());
            }
            ordered.add(shop);
        }
        return Result.ok(ordered);
    }

    /** 按分类分页查库（不传经纬度或 Redis Geo 不可用时使用） */
    private Result queryShopPageByType(Integer typeId, Integer current) {
        Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @SuppressWarnings("unused")
    public Result queryByIdBlock(Long id, BlockException ex) {
        return Result.fail("店铺访问过于频繁，请稍后再试");
    }

    @SuppressWarnings("unused")
    public Result queryByIdFallback(Long id, Throwable e) {
        log.warn("queryShopById degraded: id={}", id, e);
        return Result.fail("店铺服务繁忙，请稍后再试");
    }

    @SuppressWarnings("unused")
    public Result queryShopByTypeBlock(Integer typeId, Integer current, Double x, Double y, BlockException ex) {
        return Result.fail("列表查询过于频繁，请稍后再试");
    }

    @SuppressWarnings("unused")
    public Result queryShopByTypeFallback(Integer typeId, Integer current, Double x, Double y, Throwable e) {
        log.warn("queryShopByType degraded: typeId={}", typeId, e);
        return Result.fail("店铺列表暂不可用，请稍后再试");
    }
}
