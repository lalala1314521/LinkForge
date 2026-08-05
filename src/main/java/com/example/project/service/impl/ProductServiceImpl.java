package com.example.project.service.impl;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.common.PageResult;
import com.example.project.dto.request.ProductCreateRequest;
import com.example.project.dto.request.ProductQueryRequest;
import com.example.project.dto.request.ProductUpdateRequest;
import com.example.project.dto.response.ProductResponse;
import com.example.project.entity.Product;
import com.example.project.mapper.ProductMapper;
import com.example.project.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductCreateRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus() == null || request.getStatus().isEmpty() ? "ON_SALE" : request.getStatus());
        productMapper.insert(product);
        log.info("[商品] 创建成功：id={}, name={}, price={}, stock={}", product.getId(), product.getName(), product.getPrice(), product.getStock());
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProductUpdateRequest request) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            product.setStatus(request.getStatus());
        }
        productMapper.update(product);
        log.info("[商品] 更新成功：id={}, name={}", id, product.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        // Q4：逻辑下架，不物理删除
        product.setStatus("OFF_SALE");
        productMapper.update(product);
        log.info("[商品] 逻辑下架：id={}, name={}", id, product.getName());
    }

    @Override
    public ProductResponse getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return toResponse(product);
    }

    @Override
    public PageResult<ProductResponse> queryPage(ProductQueryRequest request) {
        int page = request.getPage() == null ? 1 : request.getPage();
        int size = request.getSize() == null ? 10 : request.getSize();
        int offset = (page - 1) * size;
        List<Product> products = productMapper.selectPage(request.getKeyword(), request.getStatus(), offset, size);
        long total = productMapper.countPage(request.getKeyword(), request.getStatus());
        List<ProductResponse> responses = products.stream().map(this::toResponse).toList();
        return new PageResult<>(responses, total, page, size);
    }

    private ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setPrice(product.getPrice());
        response.setStock(product.getStock());
        response.setStatus(product.getStatus());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }
}
