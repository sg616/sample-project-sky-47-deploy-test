package com.example.ordersapp.controller;

import java.util.List;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ordersapp.dto.OrderRequest;
import com.example.ordersapp.dto.OrderResponse;
import com.example.ordersapp.service.OrderService;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<OrderResponse> list() {
        log.info("GET /orders-api/orders called");
        return service.list();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        log.info("GET /orders-api/orders/{} called", id);
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        log.info("POST /orders-api/orders called, customer={}", request.getCustomerName());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public OrderResponse update(@PathVariable Long id, @Valid @RequestBody OrderRequest request) {
        log.info("PUT /orders-api/orders/{} called, customer={}", id, request.getCustomerName());
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("DELETE /orders-api/orders/{} called", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
