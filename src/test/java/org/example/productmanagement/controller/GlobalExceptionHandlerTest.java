package org.example.productmanagement.controller;

import org.example.productmanagement.dto.ErrorCode;
import org.example.productmanagement.service.ProductService;
import org.example.productmanagement.service.exception.ProductNoStockException;
import org.example.productmanagement.service.exception.ProductNotFoundException;
import org.example.productmanagement.service.exception.ProductNotOnSaleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 驗證各種例外都會轉成 {code, message} 格式，且 HTTP 狀態碼與 ErrorCode 一致。
 */
@WebMvcTest(ProductController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void productNotFoundReturns404() throws Exception {
        when(productService.getById(99L)).thenThrow(new ProductNotFoundException(99L));

        assertError(mockMvc.perform(get("/products/99")), ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void productNoStockReturns400() throws Exception {
        when(productService.getById(1L)).thenThrow(new ProductNoStockException("stock is 0"));

        assertError(mockMvc.perform(get("/products/1")), ErrorCode.PRODUCT_INSUFFICIENT_STOCK);
    }

    @Test
    void productNotOnSaleReturns400() throws Exception {
        when(productService.getById(1L)).thenThrow(new ProductNotOnSaleException("not on sale"));

        assertError(mockMvc.perform(get("/products/1")), ErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    void nonNumericIdReturnsInvalidParameter() throws Exception {
        assertError(mockMvc.perform(get("/products/abc")), ErrorCode.INVALID_PARAMETER);
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        assertError(mockMvc.perform(put("/products/1")), ErrorCode.METHOD_NOT_ALLOWED);
    }

    @Test
    void unknownPathReturns404() throws Exception {
        assertError(mockMvc.perform(get("/no-such-path")), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingDetails() throws Exception {
        when(productService.getAll()).thenThrow(new IllegalStateException("db password is wrong"));

        assertError(mockMvc.perform(get("/products")), ErrorCode.INTERNAL_ERROR)
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    private ResultActions assertError(ResultActions result, ErrorCode expected) throws Exception {
        return result
                .andExpect(status().is(expected.getStatus().value()))
                .andExpect(jsonPath("$.code").value(expected.name()))
                .andExpect(jsonPath("$.message").value(expected.getMessage()));
    }
}