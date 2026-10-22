package org.example.productmanagement.controller;

import org.example.productmanagement.dto.ErrorCode;
import org.example.productmanagement.service.ProductService;
import org.example.productmanagement.service.model.CreateProductCommand;
import org.example.productmanagement.service.model.ProductResult;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 只啟動 Web 層，ProductService 用 mock 取代：驗證 POST /products 的 HTTP 轉換與輸入驗證。
 */
@WebMvcTest(ProductController.class)
class ProductControllerCreateTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void validRequestReturns201WithCreatedProduct() throws Exception {
        when(productService.create(any()))
                .thenReturn(new ProductResult(1L, "Keyboard", "Mechanical", new BigDecimal("1990"), 10, true));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Keyboard","description":"Mechanical","price":1990,"stock":10,"onSale":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.description").value("Mechanical"))
                .andExpect(jsonPath("$.price").value(1990))
                .andExpect(jsonPath("$.stock").value(10))
                .andExpect(jsonPath("$.onSale").value(true))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void requestIsConvertedToCommand() throws Exception {
        when(productService.create(any()))
                .thenReturn(new ProductResult(1L, "Keyboard", "Mechanical", new BigDecimal("1990.50"), 10, true));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Keyboard","description":"Mechanical","price":1990.50,"stock":10,"onSale":true}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateProductCommand> captor = ArgumentCaptor.forClass(CreateProductCommand.class);
        verify(productService).create(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new CreateProductCommand("Keyboard", "Mechanical", new BigDecimal("1990.50"), 10, true));
    }

    @Test
    void descriptionIsOptional() throws Exception {
        when(productService.create(any()))
                .thenReturn(new ProductResult(1L, "Mouse", null, new BigDecimal("500"), 5, false));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mouse","price":500,"stock":5,"onSale":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void zeroPriceAndStockAreAllowed() throws Exception {
        when(productService.create(any()))
                .thenReturn(new ProductResult(1L, "Freebie", null, BigDecimal.ZERO, 0, false));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Freebie","price":0,"stock":0,"onSale":false}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void blankNameReturns400() throws Exception {
        assertBadRequest("""
                {"name":"  ","price":500,"stock":5,"onSale":false}
                """, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void missingNameReturns400() throws Exception {
        assertBadRequest("""
                {"price":500,"stock":5,"onSale":false}
                """, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void negativePriceReturns400() throws Exception {
        assertBadRequest("""
                {"name":"Mouse","price":-1,"stock":5,"onSale":false}
                """, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void missingPriceReturns400() throws Exception {
        assertBadRequest("""
                {"name":"Mouse","stock":5,"onSale":false}
                """, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void negativeStockReturns400() throws Exception {
        assertBadRequest("""
                {"name":"Mouse","price":500,"stock":-1,"onSale":false}
                """, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void missingStockReturns400() throws Exception {
        assertBadRequest("""
                {"name":"Mouse","price":500,"onSale":false}
                """, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void missingOnSaleReturns400() throws Exception {
        assertBadRequest("""
                {"name":"Mouse","price":500,"stock":5}
                """, ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        assertBadRequest("{\"name\":", ErrorCode.MALFORMED_REQUEST);
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Keyboard"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage()));

        verify(productService, never()).create(any());
    }

    private void assertBadRequest(String body, ErrorCode expectedCode) throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(expectedCode.name()))
                .andExpect(jsonPath("$.message").value(expectedCode.getMessage()));

        verify(productService, never()).create(any());
    }
}
