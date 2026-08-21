package com.example.sampleapp.service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sampleapp.dto.ProductRequest;
import com.example.sampleapp.dto.ProductResponse;
import com.example.sampleapp.entity.Product;
import com.example.sampleapp.exception.NotFoundException;
import com.example.sampleapp.repository.ProductRepository;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return repository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        log.info("Creating product name={}", request.getName());
        Instant now = Instant.now();
        Product product = new Product();
        apply(product, request);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        return toResponse(repository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        log.info("Updating product id={} name={}", id, request.getName());
        Product product = findOrThrow(id);
        apply(product, request);
        product.setUpdatedAt(Instant.now());
        return toResponse(repository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting product id={}", id);
        repository.delete(findOrThrow(id));
    }

    private Product findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
    }

    private ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setStock(product.getStock());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }
}
