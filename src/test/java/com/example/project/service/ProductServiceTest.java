package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.common.PageResult;
import com.example.project.dto.request.ProductCreateRequest;
import com.example.project.dto.request.ProductQueryRequest;
import com.example.project.dto.request.ProductUpdateRequest;
import com.example.project.dto.response.ProductResponse;
import com.example.project.entity.Product;
import com.example.project.mapper.ProductMapper;
import com.example.project.service.impl.ProductServiceImpl;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 商品服务测试（批次 1）
 * <p>
 * 覆盖：创建（默认 ON_SALE）、更新、逻辑下架（delete → status=OFF_SALE，不物理删除）、
 * 查询详情、分页（keyword/status 过滤透传）、不存在抛 PRODUCT_NOT_FOUND。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("商品服务测试")
class ProductServiceTest {

    @Mock ProductMapper productMapper;

    @InjectMocks ProductServiceImpl productService;

    private Product buildProduct(Long id, String name, String status) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal("99.00"));
        p.setStock(10);
        p.setStatus(status);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private ProductCreateRequest createRequest(String status) {
        ProductCreateRequest req = new ProductCreateRequest();
        req.setName("新品");
        req.setPrice(new BigDecimal("19.90"));
        req.setStock(5);
        req.setStatus(status);
        return req;
    }

    // ---- create ----

    @Test
    @DisplayName("create - 正常：插入并返回 id（默认 ON_SALE）")
    void create_success() {
        org.mockito.Mockito.doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));

        Long id = productService.create(createRequest(null));

        BDDAssertions.then(id).isEqualTo(1L);
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(captor.capture());
        BDDAssertions.then(captor.getValue().getStatus()).isEqualTo("ON_SALE");
    }

    @Test
    @DisplayName("create - 显式传状态保留")
    void create_withStatus() {
        org.mockito.Mockito.doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(2L);
            return null;
        }).when(productMapper).insert(any(Product.class));

        productService.create(createRequest("OFF_SALE"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(captor.capture());
        BDDAssertions.then(captor.getValue().getStatus()).isEqualTo("OFF_SALE");
    }

    // ---- update ----

    @Test
    @DisplayName("update - 正常：更新字段并落库")
    void update_success() {
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, "旧名", "ON_SALE"));

        ProductUpdateRequest req = new ProductUpdateRequest();
        req.setName("新名");
        req.setPrice(new BigDecimal("88.00"));
        req.setStock(20);
        req.setStatus("ON_SALE");
        productService.update(1L, req);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).update(captor.capture());
        BDDAssertions.then(captor.getValue().getName()).isEqualTo("新名");
        BDDAssertions.then(captor.getValue().getPrice()).isEqualByComparingTo("88.00");
        BDDAssertions.then(captor.getValue().getStock()).isEqualTo(20);
    }

    @Test
    @DisplayName("update - 商品不存在抛 PRODUCT_NOT_FOUND")
    void update_notFound() {
        given(productMapper.selectById(1L)).willReturn(null);

        ProductUpdateRequest req = new ProductUpdateRequest();
        req.setName("新名");
        req.setPrice(new BigDecimal("88.00"));
        req.setStock(20);

        assertThatThrownBy(() -> productService.update(1L, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verify(productMapper, never()).update(any(Product.class));
    }

    // ---- delete（逻辑下架）----

    @Test
    @DisplayName("delete - 逻辑下架：status → OFF_SALE，不物理删除")
    void delete_logicalOffSale() {
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, "耳机", "ON_SALE"));

        productService.delete(1L);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).update(captor.capture());
        BDDAssertions.then(captor.getValue().getStatus()).isEqualTo("OFF_SALE");
        BDDAssertions.then(captor.getValue().getName()).isEqualTo("耳机");   // 其余字段保留
    }

    @Test
    @DisplayName("delete - 商品不存在抛 PRODUCT_NOT_FOUND")
    void delete_notFound() {
        given(productMapper.selectById(1L)).willReturn(null);

        assertThatThrownBy(() -> productService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verify(productMapper, never()).update(any(Product.class));
    }

    // ---- getById ----

    @Test
    @DisplayName("getById - 正常映射响应")
    void getById_success() {
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, "耳机", "ON_SALE"));

        ProductResponse resp = productService.getById(1L);

        BDDAssertions.then(resp.getId()).isEqualTo(1L);
        BDDAssertions.then(resp.getName()).isEqualTo("耳机");
        BDDAssertions.then(resp.getStatus()).isEqualTo("ON_SALE");
    }

    @Test
    @DisplayName("getById - 商品不存在抛 PRODUCT_NOT_FOUND")
    void getById_notFound() {
        given(productMapper.selectById(1L)).willReturn(null);

        assertThatThrownBy(() -> productService.getById(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    // ---- queryPage ----

    @Test
    @DisplayName("queryPage - 分页查询，keyword/status 过滤透传")
    void queryPage_filters() {
        given(productMapper.selectPage("耳机", "ON_SALE", 0, 10))
                .willReturn(List.of(buildProduct(1L, "耳机A", "ON_SALE"), buildProduct(2L, "耳机B", "ON_SALE")));
        given(productMapper.countPage("耳机", "ON_SALE")).willReturn(2L);

        ProductQueryRequest req = new ProductQueryRequest();
        req.setKeyword("耳机");
        req.setStatus("ON_SALE");
        req.setPage(1);
        req.setSize(10);

        PageResult<ProductResponse> result = productService.queryPage(req);

        BDDAssertions.then(result.getRecords()).hasSize(2);
        BDDAssertions.then(result.getTotal()).isEqualTo(2);
        BDDAssertions.then(result.getPage()).isEqualTo(1);
        BDDAssertions.then(result.getSize()).isEqualTo(10);
        verify(productMapper).selectPage(eq("耳机"), eq("ON_SALE"), eq(0), eq(10));
    }

    @Test
    @DisplayName("queryPage - 空条件返回空列表")
    void queryPage_empty() {
        // keyword/status 为 null：any() 可匹配 null（anyString 不匹配 null）
        given(productMapper.selectPage(any(), any(), eq(0), eq(10))).willReturn(List.of());
        given(productMapper.countPage(any(), any())).willReturn(0L);

        ProductQueryRequest req = new ProductQueryRequest();
        req.setKeyword(null);
        req.setStatus(null);

        PageResult<ProductResponse> result = productService.queryPage(req);

        BDDAssertions.then(result.getRecords()).isEmpty();
        BDDAssertions.then(result.getTotal()).isEqualTo(0);
    }
}
