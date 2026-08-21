package com.example.ordersapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ordersapp.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
