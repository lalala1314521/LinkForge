package com.example.project.service;

import com.example.project.common.PageResult;
import com.example.project.dto.request.ProductCreateRequest;
import com.example.project.dto.request.ProductQueryRequest;
import com.example.project.dto.request.ProductUpdateRequest;
import com.example.project.dto.response.ProductResponse;

/**
 * 商品服务接口
 * <p>
 * 删除 = 逻辑下架（status=OFF_SALE），保护历史订单引用（Q4）。
 */
public interface ProductService {

    Long create(ProductCreateRequest request);

    void update(Long id, ProductUpdateRequest request);

    /**
     * 逻辑下架：status → OFF_SALE，不物理删除
     */
    void delete(Long id);

    ProductResponse getById(Long id);

    PageResult<ProductResponse> queryPage(ProductQueryRequest request);
}
