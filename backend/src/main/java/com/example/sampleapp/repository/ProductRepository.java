package com.example.sampleapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.sampleapp.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
