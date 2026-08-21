package com.example.ordersapp.service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ordersapp.dto.OrderRequest;
import com.example.ordersapp.dto.OrderResponse;
import com.example.ordersapp.entity.Order;
import com.example.ordersapp.exception.NotFoundException;
import com.example.ordersapp.repository.OrderRepository;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return repository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        log.info("Creating order customer={}", request.getCustomerName());
        Instant now = Instant.now();
        Order order = new Order();
        apply(order, request);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return toResponse(repository.save(order));
    }

    @Transactional
    public OrderResponse update(Long id, OrderRequest request) {
        log.info("Updating order id={} customer={}", id, request.getCustomerName());
        Order order = findOrThrow(id);
        apply(order, request);
        order.setUpdatedAt(Instant.now());
        return toResponse(repository.save(order));
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting order id={}", id);
        repository.delete(findOrThrow(id));
    }

    private Order findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    private void apply(Order order, OrderRequest request) {
        order.setCustomerName(request.getCustomerName());
        order.setProductName(request.getProductName());
        order.setQuantity(request.getQuantity());
        order.setTotalAmount(request.getTotalAmount());
        order.setStatus(request.getStatus());
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setCustomerName(order.getCustomerName());
        response.setProductName(order.getProductName());
        response.setQuantity(order.getQuantity());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }
}
