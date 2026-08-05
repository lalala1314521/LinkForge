package com.example.project.service;

/**
 * 库存服务接口
 * <p>
 * 扣减原子性：复用 ProductMapper.decrementStock（WHERE stock &gt;= qty 条件更新）。
 * 回退的幂等靠消费端 order_process_record 幂等表（incrementStock 无条件自增，
 * 若脱离幂等表重复执行会多加库存——必须在 processCancelled 的 @Transactional + 幂等占位内调用）。
 */
public interface InventoryService {

    /**
     * 条件扣减库存
     *
     * @return true=扣减成功，false=库存不足
     */
    boolean deduct(Long productId, Integer quantity);

    /**
     * 按订单回退库存：查 order_items 逐条 incrementStock
     */
    void restoreByOrder(Long orderId);
}
