package com.loja.admindashboard.application.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.loja.admindashboard.application.dto.OrderDetailsDTO;
import com.loja.admindashboard.application.dto.OrderItemDTO;
import com.loja.admindashboard.application.dto.ShippingAddressDTO;
import com.loja.admindashboard.domain.port.in.GetOrderDetailsUseCase;
import com.loja.ordercheckout.domain.model.Order;
import com.loja.ordercheckout.domain.model.OrderLine;
import com.loja.ordercheckout.domain.port.out.OrderRepositoryPort;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application service for the admin order-detail flow.
 */
@ApplicationScoped
public class GetOrderDetailsService implements GetOrderDetailsUseCase {

    private final OrderRepositoryPort orderRepository;

    @Inject
    public GetOrderDetailsService(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Optional<OrderDetailsDTO> findById(String orderId) {
        return orderRepository.findById(orderId).map(this::toOrderDetailsDTO);
    }

    private OrderDetailsDTO toOrderDetailsDTO(Order order) {
        return new OrderDetailsDTO(
                order.getId(),
                order.getStatus(),
                order.getCustomerEmail(),
                order.getCreatedAt(),
                toShippingAddressDTO(order.getShippingAddress()),
                toOrderItemDTOs(order.getItems()),
                order.getShippingCost(),
                order.getTotal(),
                order.getTrackingNumber()
        );
    }

    private ShippingAddressDTO toShippingAddressDTO(com.loja.ordercheckout.domain.model.ShippingAddress address) {
        if (address == null) {
            return null;
        }
        return new ShippingAddressDTO(
                address.getRecipientName(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState(),
                address.getPostalCode()
        );
    }

    private List<OrderItemDTO> toOrderItemDTOs(List<OrderLine> items) {
        return items.stream()
                .map(this::toOrderItemDTO)
                .collect(Collectors.toList());
    }

    private OrderItemDTO toOrderItemDTO(OrderLine item) {
        return new OrderItemDTO(
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }
}
