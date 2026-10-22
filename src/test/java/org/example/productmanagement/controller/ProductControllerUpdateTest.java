package org.example.productmanagement.controller;

import org.example.productmanagement.dto.ErrorCode;
import org.example.productmanagement.service.ProductService;
import org.example.productmanagement.service.model.ProductResult;
import org.example.productmanagement.service.model.UpdateProductCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 只啟動 Web 層，ProductService 用 mock 取代：驗證 PATCH /products/{id} 的部分更新與輸入驗證。
 */
@WebMvcTest(ProductController.class)
class ProductControllerUpdateTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void partialRequestReturns200WithUpdatedProduct() throws Exception {
        when(productService.update(any()))
                .thenReturn(new ProductResult(1L, "Keyboard", "Mechanical", new BigDecimal("1490"), 10, true));

        mockMvc.perform(patch("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price":1490}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.price").value(1490));
    }

    @Test
    void missingFieldsAreConvertedToNull() throws Exception {
        when(productService.update(any()))
                .thenReturn(new ProductResult(1L, "Keyboard", null, new BigDecimal("1490"), 10, false));

        mockMvc.perform(patch("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price":1490,"onSale":false}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateProductCommand> captor = ArgumentCaptor.forClass(UpdateProductCommand.class);
        verify(productService).update(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new UpdateProductCommand(1L, null, null, new BigDecimal("1490"), null, false));
    }

    @Test
    void emptyBodyIsAllowed() throws Exception {
        when(productService.update(any()))
                .thenReturn(new ProductResult(1L, "Keyboard", null, new BigDecimal("1990"), 10, true));

        mockMvc.perform(patch("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void blankNameReturns400() throws Exception {
        assertValidationFailed("""
                {"name":"  "}
                """);
    }

    @Test
    void emptyNameReturns400() throws Exception {
        assertValidationFailed("""
                {"name":""}
                """);
    }

    @Test
    void negativePriceReturns400() throws Exception {
        assertValidationFailed("""
                {"price":-1}
                """);
    }

    @Test
    void negativeStockReturns400() throws Exception {
        assertValidationFailed("""
                {"stock":-1}
                """);
    }

    private void assertValidationFailed(String body) throws Exception {
        mockMvc.perform(patch("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()));

        verify(productService, never()).update(any());
    }
}
